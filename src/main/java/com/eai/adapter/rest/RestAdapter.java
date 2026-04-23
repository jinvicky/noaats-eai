package com.eai.adapter.rest;

import com.eai.adapter.spi.Adapter;
import com.eai.adapter.spi.AdapterContext;
import com.eai.adapter.spi.Capabilities;
import com.eai.adapter.spi.DataRecord;
import com.eai.adapter.spi.WriteResult;
import com.eai.domain.AdapterType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

@Component
public class RestAdapter implements Adapter {

    private final ObjectMapper mapper;
    private final HttpClient http;

    public RestAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override public AdapterType type() { return AdapterType.REST; }
    @Override public Capabilities capabilities() { return Capabilities.readWrite(); }

    @Override
    public BoundAdapter bind(String configJson) {
        RestConfig cfg = parseConfig(configJson);
        return new Bound(cfg);
    }

    RestConfig parseConfig(String json) {
        try { return mapper.readValue(json == null ? "{}" : json, RestConfig.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid REST config: " + e.getMessage(), e); }
    }

    class Bound implements BoundAdapter {
        private final RestConfig cfg;

        Bound(RestConfig cfg) { this.cfg = cfg; }

        @Override
        public Stream<DataRecord> read(AdapterContext ctx) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.url))
                    .timeout(Duration.ofSeconds(cfg.timeoutSec))
                    .method(cfg.method == null ? "GET" : cfg.method.toUpperCase(),
                            cfg.body == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofString(cfg.body));
            applyHeaders(b);
            HttpResponse<String> resp;
            try { resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
            catch (Exception e) { throw new RuntimeException("REST read failed: " + e.getMessage(), e); }
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("REST " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            return parseBody(resp.body());
        }

        @Override
        public WriteResult write(Stream<DataRecord> records, AdapterContext ctx) {
            AtomicLong written = new AtomicLong();
            AtomicLong failed = new AtomicLong();
            records.forEach(r -> {
                try {
                    String body = mapper.writeValueAsString(r.fields());
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.url))
                            .timeout(Duration.ofSeconds(cfg.timeoutSec))
                            .method(cfg.method == null ? "POST" : cfg.method.toUpperCase(),
                                    HttpRequest.BodyPublishers.ofString(body));
                    b.header("Content-Type", "application/json");
                    applyHeaders(b);
                    HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() >= 400) {
                        failed.incrementAndGet();
                    } else {
                        written.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
            return new WriteResult(written.get(), failed.get(),
                    "REST wrote " + written.get() + " (failed " + failed.get() + ")");
        }

        private void applyHeaders(HttpRequest.Builder b) {
            if (cfg.headers != null) cfg.headers.forEach(b::header);
        }

        private Stream<DataRecord> parseBody(String body) {
            if (body == null || body.isBlank()) return Stream.empty();
            try {
                JsonNode node = mapper.readTree(body);
                if (node.isArray()) {
                    Iterator<JsonNode> it = node.elements();
                    Iterator<DataRecord> recs = new Iterator<>() {
                        @Override public boolean hasNext() { return it.hasNext(); }
                        @Override public DataRecord next() { return toRecord(it.next()); }
                    };
                    return StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(recs, 0), false);
                }
                return Stream.of(toRecord(node));
            } catch (Exception e) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("body", body);
                return Stream.of(DataRecord.of(raw));
            }
        }

        private DataRecord toRecord(JsonNode n) {
            Map<String, Object> m;
            try {
                m = mapper.convertValue(n, new TypeReference<LinkedHashMap<String, Object>>() {});
            } catch (Exception e) {
                m = new LinkedHashMap<>();
                m.put("value", n.asText());
            }
            return DataRecord.of(m == null ? new LinkedHashMap<>() : m);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    public static class RestConfig {
        public String url;
        public String method;
        public Map<String, String> headers = new HashMap<>();
        public String body;
        public int timeoutSec = 30;
    }
}
