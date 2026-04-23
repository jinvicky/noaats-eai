package com.eai.adapter.file;

import com.eai.adapter.spi.Adapter;
import com.eai.adapter.spi.AdapterContext;
import com.eai.adapter.spi.Capabilities;
import com.eai.adapter.spi.DataRecord;
import com.eai.adapter.spi.WriteResult;
import com.eai.domain.AdapterType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class FileAdapter implements Adapter {

    private final ObjectMapper json;
    private final XmlMapper xml = new XmlMapper();

    public FileAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override public AdapterType type() { return AdapterType.FILE; }
    @Override public Capabilities capabilities() { return Capabilities.readWrite(); }

    @Override
    public BoundAdapter bind(String configJson) {
        FileConfig cfg = parseConfig(configJson);
        return new Bound(cfg);
    }

    FileConfig parseConfig(String j) {
        try { return json.readValue(j == null ? "{}" : j, FileConfig.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid FILE config: " + e.getMessage(), e); }
    }

    class Bound implements BoundAdapter {
        private final FileConfig cfg;
        private Reader openReader;

        Bound(FileConfig cfg) { this.cfg = cfg; }

        @Override
        public Stream<DataRecord> read(AdapterContext ctx) {
            Path p = resolveReadPath(ctx);
            try {
                String fmt = effectiveFormat();
                switch (fmt) {
                    case "csv": return readCsv(p);
                    case "xml": return readXml(p);
                    case "json":
                    default:    return readJson(p);
                }
            } catch (IOException e) {
                throw new RuntimeException("FILE read failed: " + e.getMessage(), e);
            }
        }

        @Override
        public WriteResult write(Stream<DataRecord> records, AdapterContext ctx) {
            Path p = resolveWritePath();
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                String fmt = effectiveFormat();
                AtomicLong count = new AtomicLong();
                switch (fmt) {
                    case "csv"  -> writeCsv(p, records, count);
                    case "xml"  -> writeXml(p, records, count);
                    case "json" -> writeJson(p, records, count);
                    default     -> writeJson(p, records, count);
                }
                return new WriteResult(count.get(), 0, "FILE wrote " + count.get() + " records to " + p);
            } catch (IOException e) {
                throw new RuntimeException("FILE write failed: " + e.getMessage(), e);
            }
        }

        private Path resolveReadPath(AdapterContext ctx) {
            Object triggerPath = ctx.triggerMeta() == null ? null : ctx.triggerMeta().get("filePath");
            if (triggerPath != null) return Path.of(triggerPath.toString());
            if (cfg.path == null || cfg.path.isBlank()) {
                throw new IllegalArgumentException("FILE source requires either config.path or trigger filePath");
            }
            return Path.of(cfg.path);
        }

        private Path resolveWritePath() {
            if (cfg.path == null || cfg.path.isBlank()) {
                throw new IllegalArgumentException("FILE target requires config.path");
            }
            return Path.of(cfg.path);
        }

        private String effectiveFormat() {
            if (cfg.format != null) return cfg.format.toLowerCase();
            String path = cfg.path == null ? "" : cfg.path.toLowerCase();
            if (path.endsWith(".csv")) return "csv";
            if (path.endsWith(".xml")) return "xml";
            return "json";
        }

        @Override public void close() {
            if (openReader != null) try { openReader.close(); } catch (IOException ignored) {}
        }

        private Stream<DataRecord> readJson(Path p) throws IOException {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            if (body.isBlank()) return Stream.empty();
            JsonNode n = json.readTree(body);
            if (n.isArray()) {
                Iterator<JsonNode> it = n.elements();
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                        new Iterator<DataRecord>() {
                            @Override public boolean hasNext() { return it.hasNext(); }
                            @Override public DataRecord next() { return toRecord(it.next()); }
                        }, 0), false);
            }
            return Stream.of(toRecord(n));
        }

        private Stream<DataRecord> readCsv(Path p) throws IOException {
            BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8);
            openReader = r;
            Iterator<CSVRecord> it = CSVFormat.DEFAULT.builder()
                    .setHeader().setSkipHeaderRecord(true).build().parse(r).iterator();
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                    new Iterator<DataRecord>() {
                        @Override public boolean hasNext() { return it.hasNext(); }
                        @Override public DataRecord next() {
                            CSVRecord rec = it.next();
                            Map<String, Object> m = new LinkedHashMap<>();
                            rec.toMap().forEach(m::put);
                            return DataRecord.of(m);
                        }
                    }, 0), false).onClose(() -> { try { r.close(); } catch (IOException ignored) {} });
        }

        private Stream<DataRecord> readXml(Path p) throws IOException {
            JsonNode n = xml.readTree(Files.readAllBytes(p));
            return Stream.of(toRecord(n));
        }

        private void writeJson(Path p, Stream<DataRecord> records, AtomicLong count) throws IOException {
            List<Map<String, Object>> all = new ArrayList<>();
            records.forEach(r -> { all.add(r.fields()); count.incrementAndGet(); });
            Files.writeString(p, json.writerWithDefaultPrettyPrinter().writeValueAsString(all),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        private void writeCsv(Path p, Stream<DataRecord> records, AtomicLong count) throws IOException {
            List<DataRecord> buffered = records.toList();
            LinkedHashSet<String> headers = new LinkedHashSet<>();
            for (DataRecord r : buffered) headers.addAll(r.fields().keySet());
            try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8);
                 CSVPrinter out = new CSVPrinter(w, CSVFormat.DEFAULT.builder()
                         .setHeader(headers.toArray(String[]::new)).build())) {
                for (DataRecord r : buffered) {
                    Object[] row = headers.stream().map(h -> r.fields().get(h)).toArray();
                    out.printRecord(row);
                    count.incrementAndGet();
                }
            }
        }

        private void writeXml(Path p, Stream<DataRecord> records, AtomicLong count) throws IOException {
            List<Map<String, Object>> all = new ArrayList<>();
            records.forEach(r -> { all.add(r.fields()); count.incrementAndGet(); });
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("record", all);
            Files.writeString(p, xml.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        private DataRecord toRecord(JsonNode n) {
            Map<String, Object> m;
            try { m = json.convertValue(n, new TypeReference<LinkedHashMap<String, Object>>() {}); }
            catch (Exception e) { m = new LinkedHashMap<>(); m.put("value", n.asText()); }
            return DataRecord.of(m == null ? new LinkedHashMap<>() : m);
        }
    }

    public static class FileConfig {
        public String path;
        public String format;
    }
}
