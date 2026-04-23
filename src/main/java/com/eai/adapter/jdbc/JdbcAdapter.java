package com.eai.adapter.jdbc;

import com.eai.adapter.spi.Adapter;
import com.eai.adapter.spi.AdapterContext;
import com.eai.adapter.spi.Capabilities;
import com.eai.adapter.spi.DataRecord;
import com.eai.adapter.spi.WriteResult;
import com.eai.domain.AdapterType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

@Component
public class JdbcAdapter implements Adapter {

    private final ObjectMapper mapper;

    public JdbcAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public AdapterType type() { return AdapterType.JDBC; }
    @Override public Capabilities capabilities() { return Capabilities.readWrite(); }

    @Override
    public BoundAdapter bind(String configJson) {
        JdbcConfig cfg = parseConfig(configJson);
        return new Bound(cfg);
    }

    JdbcConfig parseConfig(String j) {
        try { return mapper.readValue(j == null ? "{}" : j, JdbcConfig.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JDBC config: " + e.getMessage(), e); }
    }

    class Bound implements BoundAdapter {
        private final JdbcConfig cfg;
        private Connection conn;

        Bound(JdbcConfig cfg) { this.cfg = cfg; }

        private Connection connection() throws SQLException {
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(cfg.url, cfg.username, cfg.password);
            }
            return conn;
        }

        @Override
        public Stream<DataRecord> read(AdapterContext ctx) {
            try {
                Connection c = connection();
                PreparedStatement ps = c.prepareStatement(cfg.query);
                ps.setFetchSize(cfg.fetchSize > 0 ? cfg.fetchSize : 500);
                ResultSet rs = ps.executeQuery();
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                Iterator<DataRecord> it = new Iterator<>() {
                    boolean nextKnown = false, hasNext = false;
                    @Override public boolean hasNext() {
                        if (!nextKnown) {
                            try { hasNext = rs.next(); nextKnown = true; }
                            catch (SQLException e) { throw new RuntimeException(e); }
                        }
                        return hasNext;
                    }
                    @Override public DataRecord next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        nextKnown = false;
                        try {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= cols; i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                            return DataRecord.of(row);
                        } catch (SQLException e) { throw new RuntimeException(e); }
                    }
                };
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, 0), false)
                        .onClose(() -> {
                            try { rs.close(); } catch (SQLException ignored) {}
                            try { ps.close(); } catch (SQLException ignored) {}
                        });
            } catch (SQLException e) {
                throw new RuntimeException("JDBC read failed: " + e.getMessage(), e);
            }
        }

        @Override
        public WriteResult write(Stream<DataRecord> records, AdapterContext ctx) {
            if (cfg.writeSql == null || cfg.writeSql.isBlank()) {
                throw new IllegalArgumentException("JDBC writeSql is required for target adapter");
            }
            AtomicLong written = new AtomicLong();
            AtomicLong failed = new AtomicLong();
            try {
                Connection c = connection();
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(cfg.writeSql)) {
                    List<String> params = cfg.writeParams == null ? List.of() : cfg.writeParams;
                    records.forEach(r -> {
                        try {
                            for (int i = 0; i < params.size(); i++) {
                                ps.setObject(i + 1, r.fields().get(params.get(i)));
                            }
                            ps.executeUpdate();
                            written.incrementAndGet();
                        } catch (SQLException e) {
                            failed.incrementAndGet();
                        }
                    });
                    c.commit();
                } catch (RuntimeException e) {
                    c.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("JDBC write failed: " + e.getMessage(), e);
            }
            return new WriteResult(written.get(), failed.get(),
                    "JDBC wrote " + written.get() + " rows (failed " + failed.get() + ")");
        }

        @Override public void close() {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public static class JdbcConfig {
        public String url;
        public String username;
        public String password;
        public String query;
        public String writeSql;
        public List<String> writeParams;
        public int fetchSize = 500;
    }
}
