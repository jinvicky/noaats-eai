package com.eai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adapter_config")
public class AdapterConfigEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdapterType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AdapterDirection direction;

    @Lob
    @Column(name = "config_json", nullable = false)
    private String configJson;

    @Column(name = "system_code", length = 40)
    private String systemCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AdapterType getType() { return type; }
    public void setType(AdapterType type) { this.type = type; }
    public AdapterDirection getDirection() { return direction; }
    public void setDirection(AdapterDirection direction) { this.direction = direction; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
