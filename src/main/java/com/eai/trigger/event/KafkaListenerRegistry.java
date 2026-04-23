package com.eai.trigger.event;

import com.eai.domain.TriggerType;
import com.eai.execution.ExecutionEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaListenerRegistry {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerRegistry.class);

    private final ExecutionEngine engine;
    private final String bootstrapServers;
    private final Map<String, KafkaMessageListenerContainer<String, String>> containers = new ConcurrentHashMap<>();

    public KafkaListenerRegistry(ExecutionEngine engine,
                                 @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.engine = engine;
        this.bootstrapServers = bootstrapServers;
    }

    public void register(String interfaceId, String topic, String groupId) {
        unregister(interfaceId);
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId == null ? "eai-" + interfaceId : groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties cp = new ContainerProperties(topic);
        cp.setMessageListener((MessageListener<String, String>) record -> {
            try {
                engine.start(interfaceId, TriggerType.EVENT, Map.of(
                        "topic", topic,
                        "key", record.key() == null ? "" : record.key(),
                        "payload", record.value()));
            } catch (Exception e) {
                log.error("Kafka trigger failed for {}: {}", interfaceId, e.getMessage(), e);
            }
        });

        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(cf, cp);
        try {
            container.start();
            containers.put(interfaceId, container);
            log.info("Kafka listener started for interface {} on topic {}", interfaceId, topic);
        } catch (Exception e) {
            log.warn("Kafka listener could not start (broker unreachable?): {}", e.getMessage());
        }
    }

    public void unregister(String interfaceId) {
        KafkaMessageListenerContainer<String, String> c = containers.remove(interfaceId);
        if (c != null) try { c.stop(); } catch (Exception ignored) {}
    }

    @PreDestroy
    public void stopAll() {
        containers.values().forEach(c -> { try { c.stop(); } catch (Exception ignored) {} });
        containers.clear();
    }
}
