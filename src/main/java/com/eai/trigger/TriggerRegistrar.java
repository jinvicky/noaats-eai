package com.eai.trigger;

import com.eai.domain.InterfaceDef;
import com.eai.domain.InterfaceDefRepository;
import com.eai.domain.InterfaceVersion;
import com.eai.domain.InterfaceVersionRepository;
import com.eai.domain.TriggerType;
import com.eai.trigger.cron.CronTriggerRegistry;
import com.eai.trigger.event.KafkaListenerRegistry;
import com.eai.trigger.file.FileWatcherService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TriggerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TriggerRegistrar.class);

    private final InterfaceDefRepository interfaces;
    private final InterfaceVersionRepository versions;
    private final CronTriggerRegistry cron;
    private final KafkaListenerRegistry kafka;
    private final FileWatcherService fileWatcher;
    private final ObjectMapper mapper;

    public TriggerRegistrar(InterfaceDefRepository interfaces,
                            InterfaceVersionRepository versions,
                            CronTriggerRegistry cron,
                            KafkaListenerRegistry kafka,
                            FileWatcherService fileWatcher,
                            ObjectMapper mapper) {
        this.interfaces = interfaces;
        this.versions = versions;
        this.cron = cron;
        this.kafka = kafka;
        this.fileWatcher = fileWatcher;
        this.mapper = mapper;
    }

    @PostConstruct
    public void registerAll() {
        List<InterfaceDef> active = interfaces.findByActiveTrue();
        log.info("Registering triggers for {} active interface(s)", active.size());
        for (InterfaceDef d : active) registerOne(d.getId());
    }

    @EventListener
    public void onChanged(InterfaceChangedEvent event) {
        unregisterOne(event.interfaceId());
        if (event.active()) registerOne(event.interfaceId());
    }

    public void registerOne(String interfaceId) {
        InterfaceVersion v = versions.findTopByInterfaceIdOrderByVersionNoDesc(interfaceId).orElse(null);
        if (v == null) return;
        TriggerConfig cfg = parse(v.getTriggerConfigJson());
        if (cfg == null || cfg.type == null) return;
        try {
            switch (cfg.type) {
                case CRON -> { if (cfg.cron != null) cron.register(interfaceId, cfg.cron); }
                case EVENT -> { if (cfg.topic != null) kafka.register(interfaceId, cfg.topic, cfg.groupId); }
                case FILE -> { if (cfg.watchPath != null) fileWatcher.register(interfaceId, cfg.watchPath); }
                case MANUAL -> {}
            }
            log.info("Registered {} trigger for interface {}", cfg.type, interfaceId);
        } catch (Exception e) {
            log.error("Failed to register trigger for {}: {}", interfaceId, e.getMessage(), e);
        }
    }

    public void unregisterOne(String interfaceId) {
        cron.unregister(interfaceId);
        kafka.unregister(interfaceId);
        fileWatcher.unregister(interfaceId);
    }

    private TriggerConfig parse(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, TriggerConfig.class); }
        catch (Exception e) { log.warn("Bad trigger_config: {}", e.getMessage()); return null; }
    }

    public record InterfaceChangedEvent(String interfaceId, boolean active) {}

    public record InterfaceTriggerFired(String interfaceId, TriggerType type) {}
}
