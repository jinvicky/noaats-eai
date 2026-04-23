package com.eai.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "execution_log",
        indexes = @Index(name = "idx_log_run", columnList = "run_id,seq"))
public class ExecutionLog {

    public enum Level { INFO, WARN, ERROR }
    public enum Phase { READ, TRANSFORM, WRITE, LIFECYCLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Phase phase;

    @Column(length = 2000)
    private String message;

    @Lob
    private String payloadExcerpt;

    @Column(nullable = false)
    private Instant ts;

    @PrePersist
    void onCreate() {
        if (ts == null) ts = Instant.now();
    }

    public static ExecutionLog of(String runId, long seq, Level level, Phase phase, String message) {
        ExecutionLog l = new ExecutionLog();
        l.runId = runId;
        l.seq = seq;
        l.level = level;
        l.phase = phase;
        l.message = message;
        return l;
    }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPayloadExcerpt() { return payloadExcerpt; }
    public void setPayloadExcerpt(String payloadExcerpt) { this.payloadExcerpt = payloadExcerpt; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
}
