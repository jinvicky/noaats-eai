package com.eai.web;

import com.eai.domain.AdapterConfigEntity;
import com.eai.domain.AdapterConfigRepository;
import com.eai.domain.ExecutionRun;
import com.eai.domain.ExecutionRunRepository;
import com.eai.domain.InterfaceDef;
import com.eai.domain.InterfaceDefRepository;
import com.eai.domain.InterfaceVersion;
import com.eai.domain.InterfaceVersionRepository;
import com.eai.domain.RegisteredSystemRepository;
import com.eai.domain.RunStatus;
import com.eai.domain.TriggerType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final ExecutionRunRepository runs;
    private final RegisteredSystemRepository systems;
    private final AdapterConfigRepository adapters;
    private final InterfaceDefRepository interfaces;
    private final InterfaceVersionRepository versions;

    public DashboardController(ExecutionRunRepository runs,
                               RegisteredSystemRepository systems,
                               AdapterConfigRepository adapters,
                               InterfaceDefRepository interfaces,
                               InterfaceVersionRepository versions) {
        this.runs = runs;
        this.systems = systems;
        this.adapters = adapters;
        this.interfaces = interfaces;
        this.versions = versions;
    }

    private static final int PAGE_SIZE = 10;

    @GetMapping("/")
    public String dashboard(
            @RequestParam(defaultValue = "24h") String window,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String interfaceId,
            @RequestParam(required = false) String systemCode,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String win = normalizeWindow(window);
        Duration dur = windowDuration(win);
        Instant now = Instant.now();
        Instant from = now.minus(dur);

        // Build map of interfaceId -> set of systemCodes (from source + target adapters)
        Map<String, Set<String>> ifaceSystems = buildInterfaceSystemIndex();

        // Pull runs in window, then apply extra filters in memory (dataset size small for MVP)
        List<ExecutionRun> inWindow = runs.findByStartedAtAfterOrderByStartedAtAsc(from);
        List<ExecutionRun> filtered = inWindow.stream()
                .filter(r -> status == null || status.isBlank()
                        || r.getStatus() != null && r.getStatus().name().equals(status))
                .filter(r -> triggerType == null || triggerType.isBlank()
                        || r.getTriggerType() != null && r.getTriggerType().name().equals(triggerType))
                .filter(r -> interfaceId == null || interfaceId.isBlank()
                        || interfaceId.equals(r.getInterfaceId()))
                .filter(r -> systemCode == null || systemCode.isBlank()
                        || ifaceSystems.getOrDefault(r.getInterfaceId(), Set.of()).contains(systemCode))
                .toList();

        // Stats
        long total = filtered.size();
        long successCount = filtered.stream().filter(r -> r.getStatus() == RunStatus.SUCCESS).count();
        long failedCount = filtered.stream().filter(r -> r.getStatus() == RunStatus.FAILED).count();
        long partialCount = filtered.stream().filter(r -> r.getStatus() == RunStatus.PARTIAL).count();

        // Inventory cards
        long systemCountVal = (systemCode != null && !systemCode.isBlank()) ? 1 : systems.count();
        long adapterCountVal = (systemCode != null && !systemCode.isBlank())
                ? adapters.findAll().stream().filter(a -> systemCode.equals(a.getSystemCode())).count()
                : adapters.count();
        long interfaceCountVal = (systemCode != null && !systemCode.isBlank())
                ? ifaceSystems.values().stream().filter(s -> s.contains(systemCode)).count()
                : interfaces.count();

        // Buckets
        int bucketCount = bucketCount(win);
        Duration bucketDur = dur.dividedBy(bucketCount);
        List<ThroughputBucket> buckets = buildBuckets(filtered, from, bucketCount, bucketDur, win);
        long yMax = Math.max(1, buckets.stream()
                .mapToLong(b -> Math.max(b.read(), b.written())).max().orElse(0));
        long totalRead = buckets.stream().mapToLong(ThroughputBucket::read).sum();
        long totalWritten = buckets.stream().mapToLong(ThroughputBucket::written).sum();

        // Recent runs: sorted by startedAt desc, paginated by PAGE_SIZE
        List<ExecutionRun> sortedFiltered = filtered.stream()
                .sorted(Comparator.comparing(ExecutionRun::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int totalRecent = sortedFiltered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalRecent / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int offset = safePage * PAGE_SIZE;
        int end = Math.min(offset + PAGE_SIZE, totalRecent);
        List<ExecutionRun> recent = offset >= totalRecent
                ? List.of()
                : sortedFiltered.subList(offset, end);

        // Interface name lookup for the recent table
        Map<String, String> interfaceNames = new HashMap<>();
        for (InterfaceDef d : interfaces.findAll()) interfaceNames.put(d.getId(), d.getName());

        model.addAttribute("window", win);
        model.addAttribute("selStatus", status);
        model.addAttribute("selTriggerType", triggerType);
        model.addAttribute("selInterfaceId", interfaceId);
        model.addAttribute("selSystemCode", systemCode);
        model.addAttribute("interfacesList", interfaces.findAllByOrderByCreatedAtDesc());
        model.addAttribute("systemsList", systems.findAllByOrderByCodeAsc());
        model.addAttribute("statusOptions", RunStatus.values());
        model.addAttribute("triggerOptions", TriggerType.values());
        model.addAttribute("windowOptions", List.of("1h", "6h", "24h", "7d"));

        model.addAttribute("windowLabel", windowLabel(win));
        model.addAttribute("last24", total);
        model.addAttribute("success24", successCount);
        model.addAttribute("failed24", failedCount);
        model.addAttribute("partial24", partialCount);
        model.addAttribute("systemCount", systemCountVal);
        model.addAttribute("adapterCount", adapterCountVal);
        model.addAttribute("interfaceCount", interfaceCountVal);
        model.addAttribute("recent", recent);
        model.addAttribute("interfaceNames", interfaceNames);

        // Pagination metadata
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalRecent", totalRecent);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("pageFrom", totalRecent == 0 ? 0 : offset + 1);
        model.addAttribute("pageTo", end);
        model.addAttribute("pageNumbers", buildPageWindow(safePage, totalPages, 7));

        model.addAttribute("buckets", buckets);
        model.addAttribute("yMax", yMax);
        model.addAttribute("totalRead", totalRead);
        model.addAttribute("totalWritten", totalWritten);
        return "dashboard";
    }

    /**
     * Produce a compact list of page numbers to show around the current page.
     * Up to `max` contiguous page numbers, centered on current when possible.
     */
    private List<Integer> buildPageWindow(int current, int total, int max) {
        if (total <= max) {
            List<Integer> all = new ArrayList<>(total);
            for (int i = 0; i < total; i++) all.add(i);
            return all;
        }
        int half = max / 2;
        int start = Math.max(0, current - half);
        int end = Math.min(total, start + max);
        start = Math.max(0, end - max);
        List<Integer> window = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) window.add(i);
        return window;
    }

    private Map<String, Set<String>> buildInterfaceSystemIndex() {
        Map<String, String> adapterToSystem = new HashMap<>();
        for (AdapterConfigEntity a : adapters.findAll()) {
            if (a.getSystemCode() != null && !a.getSystemCode().isBlank()) {
                adapterToSystem.put(a.getId(), a.getSystemCode());
            }
        }
        Map<String, Set<String>> out = new HashMap<>();
        for (InterfaceDef d : interfaces.findAll()) {
            InterfaceVersion v = versions.findTopByInterfaceIdOrderByVersionNoDesc(d.getId()).orElse(null);
            if (v == null) continue;
            Set<String> syss = new HashSet<>();
            String s1 = adapterToSystem.get(v.getSourceAdapterId());
            String s2 = adapterToSystem.get(v.getTargetAdapterId());
            if (s1 != null) syss.add(s1);
            if (s2 != null) syss.add(s2);
            out.put(d.getId(), syss);
        }
        return out;
    }

    private static String normalizeWindow(String w) {
        if (w == null) return "24h";
        return switch (w.toLowerCase()) {
            case "1h", "6h", "24h", "7d" -> w.toLowerCase();
            default -> "24h";
        };
    }

    private static Duration windowDuration(String w) {
        return switch (w) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "7d" -> Duration.ofDays(7);
            default   -> Duration.ofHours(24);
        };
    }

    private static int bucketCount(String w) {
        return switch (w) {
            case "1h" -> 12;   // 5 min each
            case "6h" -> 12;   // 30 min each
            case "7d" -> 28;   // 6 hours each
            default   -> 24;   // 1 hour each
        };
    }

    private static String windowLabel(String w) {
        return switch (w) {
            case "1h" -> "지난 1시간";
            case "6h" -> "지난 6시간";
            case "7d" -> "지난 7일";
            default   -> "지난 24시간";
        };
    }

    private List<ThroughputBucket> buildBuckets(List<ExecutionRun> runs, Instant from,
                                                int bucketCount, Duration bucketDur, String win) {
        long[] read = new long[bucketCount];
        long[] written = new long[bucketCount];
        long[] runCount = new long[bucketCount];
        long bucketMs = bucketDur.toMillis();
        for (ExecutionRun r : runs) {
            if (r.getStartedAt() == null) continue;
            long diffMs = r.getStartedAt().toEpochMilli() - from.toEpochMilli();
            int idx = (int) (diffMs / bucketMs);
            if (idx < 0 || idx >= bucketCount) continue;
            read[idx] += r.getRecordsRead();
            written[idx] += r.getRecordsWritten();
            runCount[idx]++;
        }
        DateTimeFormatter fmt = switch (win) {
            case "7d" -> DateTimeFormatter.ofPattern("MM-dd HH:mm");
            default   -> DateTimeFormatter.ofPattern("HH:mm");
        };
        ZoneId tz = ZoneId.systemDefault();
        List<ThroughputBucket> out = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            Instant bucketStart = from.plusMillis(bucketMs * i);
            String label = LocalDateTime.ofInstant(bucketStart, tz).format(fmt);
            out.add(new ThroughputBucket(label, i, read[i], written[i], runCount[i]));
        }
        return out;
    }
}
