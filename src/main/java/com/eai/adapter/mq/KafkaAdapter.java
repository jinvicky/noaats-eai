package com.eai.adapter.mq;

import com.eai.adapter.spi.Adapter;
import com.eai.adapter.spi.AdapterContext;
import com.eai.adapter.spi.Capabilities;
import com.eai.adapter.spi.DataRecord;
import com.eai.adapter.spi.WriteResult;
import com.eai.domain.AdapterType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaAdapter implements Adapter {

    private final ObjectMapper mapper;
    private final String bootstrapServers;
    private final String kafkaApiKey;
    private final String kafkaApiSecret;

    public KafkaAdapter(ObjectMapper mapper,
                        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
                        @Value("${KAFKA_API_KEY:}") String kafkaApiKey,
                        @Value("${KAFKA_API_SECRET:}") String kafkaApiSecret) {
        this.mapper = mapper;
        this.bootstrapServers = bootstrapServers;
        this.kafkaApiKey = kafkaApiKey;
        this.kafkaApiSecret = kafkaApiSecret;
    }

    @Override public AdapterType type() { return AdapterType.KAFKA; }
    @Override public Capabilities capabilities() { return Capabilities.readWrite(); }

    @Override
    public BoundAdapter bind(String configJson) {
        KafkaConfig cfg = parseConfig(configJson);
        return new Bound(cfg);
    }

    KafkaConfig parseConfig(String j) {
        try { return mapper.readValue(j == null ? "{}" : j, KafkaConfig.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid KAFKA config: " + e.getMessage(), e); }
    }

    class Bound implements BoundAdapter {
        private final KafkaConfig cfg;
        private KafkaProducer<String, String> producer;

        Bound(KafkaConfig cfg) { this.cfg = cfg; }

        /**
         * Read semantics for Kafka: when triggered by EVENT, payload is passed via triggerMeta.
         * For manual/scheduled triggers on a Kafka source, we return a single record drawn
         * from triggerMeta.payload (or an empty stream if none — polling is done by
         * KafkaListenerRegistry, not this adapter).
         */
        @Override
        public Stream<DataRecord> read(AdapterContext ctx) {
            Object payload = ctx.triggerMeta() == null ? null : ctx.triggerMeta().get("payload");
            if (payload == null) return Stream.empty();
            return Stream.of(toRecord(payload));
        }

        @Override
        public WriteResult write(Stream<DataRecord> records, AdapterContext ctx) {
            if (cfg.topic == null || cfg.topic.isBlank()) {
                throw new IllegalArgumentException("KAFKA topic is required for target adapter");
            }
            AtomicLong written = new AtomicLong();
            AtomicLong failed = new AtomicLong();
            try (KafkaProducer<String, String> p = newProducer()) {
                producer = p;
                records.forEach(r -> {
                    try {
                        String body = mapper.writeValueAsString(r.fields());
                        String key = cfg.keyField == null ? null : Objects.toString(r.fields().get(cfg.keyField), null);
                        p.send(new ProducerRecord<>(cfg.topic, key, body)).get();
                        written.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                });
            }
            return new WriteResult(written.get(), failed.get(),
                    "Kafka wrote " + written.get() + " to " + cfg.topic + " (failed " + failed.get() + ")");
        }

        private KafkaProducer<String, String> newProducer() {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "1");
            props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
            props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 4 * 1024 * 1024L); // 4MB (default 32MB)
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, 4096); // 4KB (default 16KB)
            if (kafkaApiKey != null && !kafkaApiKey.isBlank()) {
                props.put("security.protocol", "SASL_SSL");
                props.put("sasl.mechanism", "PLAIN");
                props.put("sasl.jaas.config",
                        "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"" + kafkaApiKey + "\" password=\"" + kafkaApiSecret + "\";");
            }
            return new KafkaProducer<>(props);
        }

        private DataRecord toRecord(Object payload) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (payload instanceof String s) {
                try {
                    JsonNode n = mapper.readTree(s);
                    if (n.isObject()) {
                        m = mapper.convertValue(n, new TypeReference<LinkedHashMap<String, Object>>() {});
                    } else {
                        m.put("value", s);
                    }
                } catch (Exception e) {
                    m.put("value", s);
                }
            } else if (payload instanceof Map<?, ?> mm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) mm;
                m = new LinkedHashMap<>(cast);
            } else {
                m.put("value", Objects.toString(payload));
            }
            return DataRecord.of(m);
        }
    }

    public static class KafkaConfig {
        public String topic;
        public String groupId = "eai-consumer";
        public String keyField;
    }
}
