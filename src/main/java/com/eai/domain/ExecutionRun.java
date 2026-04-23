package com.eai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_run",
        indexes = {
                @Index(name = "idx_run_interface", columnList = "interface_id"),
                @Index(name = "idx_run_status", columnList = "status"),
                @Index(name = "idx_run_started", columnList = "startedAt")
        })
public class ExecutionRun {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "interface_id", nullable = false, length = 36)
    private String interfaceId;

    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriggerType triggerType;

    @Lob
    private String triggerMeta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    private Instant startedAt;
    private Instant endedAt;

    private long recordsRead;
    private long recordsWritten;

    @Lob
    private String errorSummary;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (startedAt == null) startedAt = Instant.now();
        if (status == null) status = RunStatus.QUEUED;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInterfaceId() { return interfaceId; }
    public void setInterfaceId(String interfaceId) { this.interfaceId = interfaceId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public String getTriggerMeta() { return triggerMeta; }
    public void setTriggerMeta(String triggerMeta) { this.triggerMeta = triggerMeta; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public long getRecordsRead() { return recordsRead; }
    public void setRecordsRead(long recordsRead) { this.recordsRead = recordsRead; }
    public long getRecordsWritten() { return recordsWritten; }
    public void setRecordsWritten(long recordsWritten) { this.recordsWritten = recordsWritten; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
}
