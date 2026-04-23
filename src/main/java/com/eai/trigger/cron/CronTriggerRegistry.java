package com.eai.trigger.cron;

import com.eai.domain.TriggerType;
import com.eai.execution.ExecutionEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class CronTriggerRegistry {

    private static final Logger log = LoggerFactory.getLogger(CronTriggerRegistry.class);

    private final ThreadPoolTaskScheduler scheduler;
    private final ExecutionEngine engine;
    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public CronTriggerRegistry(ThreadPoolTaskScheduler scheduler, ExecutionEngine engine) {
        this.scheduler = scheduler;
        this.engine = engine;
    }

    public void register(String interfaceId, String cronExpr) {
        unregister(interfaceId);
        ScheduledFuture<?> f = scheduler.schedule(
                () -> {
                    try { engine.start(interfaceId, TriggerType.CRON, Map.of("cron", cronExpr)); }
                    catch (Exception e) { log.error("Cron trigger for {} failed: {}", interfaceId, e.getMessage(), e); }
                },
                new CronTrigger(cronExpr));
        scheduled.put(interfaceId, f);
    }

    public void unregister(String interfaceId) {
        ScheduledFuture<?> f = scheduled.remove(interfaceId);
        if (f != null) f.cancel(false);
    }
}
