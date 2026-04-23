package com.eai.execution;

import com.eai.adapter.spi.Adapter;
import com.eai.adapter.spi.AdapterContext;
import com.eai.adapter.spi.AdapterFactory;
import com.eai.adapter.spi.DataRecord;
import com.eai.adapter.spi.WriteResult;
import com.eai.domain.*;
import com.eai.mapping.MappingEngine;
import com.eai.mapping.MappingRule;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final InterfaceDefRepository interfaces;
    private final InterfaceVersionRepository versions;
    private final AdapterConfigRepository adapterConfigs;
    private final ExecutionRunRepository runs;
    private final ExecutionLogRepository logs;
    private final AdapterFactory adapterFactory;
    private final MappingEngine mappingEngine;
    private final ObjectMapper mapper;
    private final Counter runsTotal;
    private final Counter runsFailed;
    private final Timer runTimer;

    public ExecutionEngine(InterfaceDefRepository interfaces,
                           InterfaceVersionRepository versions,
                           AdapterConfigRepository adapterConfigs,
                           ExecutionRunRepository runs,
                           ExecutionLogRepository logs,
                           AdapterFactory adapterFactory,
                           MappingEngine mappingEngine,
                           ObjectMapper mapper,
                           MeterRegistry meters) {
        this.interfaces = interfaces;
        this.versions = versions;
        this.adapterConfigs = adapterConfigs;
        this.runs = runs;
        this.logs = logs;
        this.adapterFactory = adapterFactory;
        this.mappingEngine = mappingEngine;
        this.mapper = mapper;
        this.runsTotal = Counter.builder("eai.runs.total").register(meters);
        this.runsFailed = Counter.builder("eai.runs.failed").register(meters);
        this.runTimer = Timer.builder("eai.runs.duration").register(meters);
    }

    public String start(String interfaceId, TriggerType triggerType, Map<String, Object> triggerMeta) {
        InterfaceDef def = interfaces.findById(interfaceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown interface: " + interfaceId));
        InterfaceVersion v = versions.findTopByInterfaceIdOrderByVersionNoDesc(def.getId())
                .orElseThrow(() -> new IllegalStateException("Interface has no versions: " + interfaceId));

        ExecutionRun run = new ExecutionRun();
        run.setInterfaceId(def.getId());
        run.setVersionNo(v.getVersionNo());
        run.setTriggerType(triggerType);
        run.setTriggerMeta(writeJsonSafe(triggerMeta));
        run.setStatus(RunStatus.QUEUED);
        runs.save(run);

        runAsync(run.getId());
        return run.getId();
    }

    @Async("eaiExecutor")
    public CompletableFuture<Void> runAsync(String runId) {
        execute(runId);
        return CompletableFuture.completedFuture(null);
    }

    public void execute(String runId) {
        ExecutionRun run = runs.findById(runId).orElseThrow();
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        runs.save(run);

        AtomicLong seq = new AtomicLong();
        AtomicLong readCount = new AtomicLong();
        long t0 = System.nanoTime();
        runsTotal.increment();

        try {
            InterfaceVersion v = versions.findTopByInterfaceIdOrderByVersionNoDesc(run.getInterfaceId()).orElseThrow();
            AdapterConfigEntity srcCfg = adapterConfigs.findById(v.getSourceAdapterId()).orElseThrow();
            AdapterConfigEntity tgtCfg = adapterConfigs.findById(v.getTargetAdapterId()).orElseThrow();

            Adapter source = adapterFactory.require(srcCfg.getType());
            Adapter target = adapterFactory.require(tgtCfg.getType());

            List<MappingRule> rules = mappingEngine.parseRules(v.getMappingRulesJson());
            logLine(runId, seq, ExecutionLog.Level.INFO, ExecutionLog.Phase.LIFECYCLE,
                    "Executing " + srcCfg.getName() + " → " + tgtCfg.getName() + " (" + rules.size() + " rules)");

            try (Adapter.BoundAdapter src = source.bind(srcCfg.getConfigJson());
                 Adapter.BoundAdapter tgt = target.bind(tgtCfg.getConfigJson())) {

                AdapterContext ctx = new AdapterContext(runId, readJsonSafe(run.getTriggerMeta()));
                Stream<DataRecord> raw = src.read(ctx).peek(r -> readCount.incrementAndGet());
                Stream<DataRecord> mapped = raw.map(r -> {
                    try {
                        return mappingEngine.apply(r, rules);
                    } catch (Exception e) {
                        logLine(runId, seq, ExecutionLog.Level.ERROR, ExecutionLog.Phase.TRANSFORM,
                                "Mapping failed: " + e.getMessage());
                        throw e;
                    }
                });
                WriteResult wr = tgt.write(mapped, ctx);

                run.setRecordsRead(readCount.get());
                run.setRecordsWritten(wr.recordsWritten());
                run.setStatus(wr.recordsFailed() > 0 ? RunStatus.PARTIAL : RunStatus.SUCCESS);
                if (wr.summary() != null) {
                    logLine(runId, seq, ExecutionLog.Level.INFO, ExecutionLog.Phase.WRITE, wr.summary());
                }
            }
        } catch (Exception e) {
            log.error("Run {} failed", runId, e);
            runsFailed.increment();
            run.setStatus(RunStatus.FAILED);
            run.setErrorSummary(e.getClass().getSimpleName() + ": " + e.getMessage());
            logLine(runId, seq, ExecutionLog.Level.ERROR, ExecutionLog.Phase.LIFECYCLE,
                    run.getErrorSummary());
        } finally {
            run.setEndedAt(Instant.now());
            runs.save(run);
            runTimer.record(java.time.Duration.ofNanos(System.nanoTime() - t0));
        }
    }

    private void logLine(String runId, AtomicLong seq, ExecutionLog.Level lvl, ExecutionLog.Phase phase, String msg) {
        logs.save(ExecutionLog.of(runId, seq.incrementAndGet(), lvl, phase, msg));
    }

    private String writeJsonSafe(Object o) {
        try { return o == null ? null : mapper.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    private Map<String, Object> readJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
