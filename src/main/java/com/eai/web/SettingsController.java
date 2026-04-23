package com.eai.web;

import com.eai.domain.ErrorPolicy;
import com.eai.domain.PlatformSettings;
import com.eai.domain.PlatformSettingsRepository;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final PlatformSettingsRepository repo;

    public SettingsController(PlatformSettingsRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String view(Model model) {
        model.addAttribute("settings", repo.loadOrDefault());
        model.addAttribute("errorPolicies", ErrorPolicy.values());
        model.addAttribute("timezones", commonTimezones());
        return "settings/edit";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam String platformName,
                       @RequestParam String timezone,
                       @RequestParam int logRetentionDays,
                       @RequestParam int defaultPageSize,
                       @RequestParam ErrorPolicy defaultErrorPolicy,
                       @RequestParam(required = false) String notifyEmail,
                       @RequestParam(required = false) String slackWebhook,
                       @RequestParam(defaultValue = "false") boolean notifyOnFailure,
                       @RequestParam(defaultValue = "false") boolean maintenanceMode,
                       @RequestParam(required = false) String maintenanceMessage,
                       RedirectAttributes ra) {
        PlatformSettings s = repo.loadOrDefault();
        s.setId(PlatformSettings.SINGLETON_ID);
        s.setPlatformName(platformName.trim());
        s.setTimezone(timezone.trim());
        s.setLogRetentionDays(Math.max(1, logRetentionDays));
        s.setDefaultPageSize(Math.max(1, Math.min(100, defaultPageSize)));
        s.setDefaultErrorPolicy(defaultErrorPolicy);
        s.setNotifyEmail(nullIfBlank(notifyEmail));
        s.setSlackWebhook(nullIfBlank(slackWebhook));
        s.setNotifyOnFailure(notifyOnFailure);
        s.setMaintenanceMode(maintenanceMode);
        s.setMaintenanceMessage(nullIfBlank(maintenanceMessage));
        repo.save(s);
        ra.addFlashAttribute("msg", "Settings saved");
        return "redirect:/settings";
    }

    private static String nullIfBlank(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    /** A curated list of commonly used timezones for the dropdown hint. */
    private static List<String> commonTimezones() {
        List<String> handy = List.of(
                "Asia/Seoul", "Asia/Tokyo", "Asia/Shanghai", "Asia/Singapore",
                "UTC", "Europe/London", "Europe/Berlin",
                "America/New_York", "America/Los_Angeles");
        // Sort available zones but put handy ones first
        List<String> all = ZoneId.getAvailableZoneIds().stream().sorted().toList();
        return java.util.stream.Stream.concat(handy.stream(),
                all.stream().filter(z -> !handy.contains(z)))
                .collect(Collectors.toList());
    }
}
