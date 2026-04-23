package com.eai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interface_version")
public class InterfaceVersion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "interface_id", nullable = false, length = 36)
    private String interfaceId;

    @Column(nullable = false)
    private int versionNo;

    @Column(length = 1000)
    private String notes;

    @Column(name = "source_adapter_id", nullable = false, length = 36)
    private String sourceAdapterId;

    @Column(name = "target_adapter_id", nullable = false, length = 36)
    private String targetAdapterId;

    @Lob
    @Column(name = "source_schema")
    private String sourceSchemaJson;

    @Lob
    @Column(name = "target_schema")
    private String targetSchemaJson;

    @Lob
    @Column(name = "mapping_rules")
    private String mappingRulesJson;

    @Lob
    @Column(name = "trigger_config")
    private String triggerConfigJson;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInterfaceId() { return interfaceId; }
    public void setInterfaceId(String interfaceId) { this.interfaceId = interfaceId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getSourceAdapterId() { return sourceAdapterId; }
    public void setSourceAdapterId(String sourceAdapterId) { this.sourceAdapterId = sourceAdapterId; }
    public String getTargetAdapterId() { return targetAdapterId; }
    public void setTargetAdapterId(String targetAdapterId) { this.targetAdapterId = targetAdapterId; }
    public String getSourceSchemaJson() { return sourceSchemaJson; }
    public void setSourceSchemaJson(String sourceSchemaJson) { this.sourceSchemaJson = sourceSchemaJson; }
    public String getTargetSchemaJson() { return targetSchemaJson; }
    public void setTargetSchemaJson(String targetSchemaJson) { this.targetSchemaJson = targetSchemaJson; }
    public String getMappingRulesJson() { return mappingRulesJson; }
    public void setMappingRulesJson(String mappingRulesJson) { this.mappingRulesJson = mappingRulesJson; }
    public String getTriggerConfigJson() { return triggerConfigJson; }
    public void setTriggerConfigJson(String triggerConfigJson) { this.triggerConfigJson = triggerConfigJson; }
    public Instant getCreatedAt() { return createdAt; }
}
