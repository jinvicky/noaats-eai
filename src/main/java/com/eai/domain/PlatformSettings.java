package com.eai.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    /** Singleton row identifier. */
    public static final String SINGLETON_ID = "global";

    @Id
    @Column(length = 20)
    private String id = SINGLETON_ID;

    @Column(nullable = false, length = 100)
    private String platformName = "EAI Platform";

    @Column(nullable = false, length = 50)
    private String timezone = "Asia/Seoul";

    @Column(nullable = false)
    private int logRetentionDays = 30;

    @Column(nullable = false)
    private int defaultPageSize = 10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ErrorPolicy defaultErrorPolicy = ErrorPolicy.FAIL_FAST;

    @Column(length = 200)
    private String notifyEmail;

    @Column(length = 500)
    private String slackWebhook;

    @Column(nullable = false)
    private boolean notifyOnFailure = true;

    @Column(nullable = false)
    private boolean maintenanceMode = false;

    @Column(length = 1000)
    private String maintenanceMessage;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 100)
    private String updatedBy;

    @PreUpdate
    @PrePersist
    void touch() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public int getLogRetentionDays() { return logRetentionDays; }
    public void setLogRetentionDays(int logRetentionDays) { this.logRetentionDays = logRetentionDays; }
    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }
    public ErrorPolicy getDefaultErrorPolicy() { return defaultErrorPolicy; }
    public void setDefaultErrorPolicy(ErrorPolicy defaultErrorPolicy) { this.defaultErrorPolicy = defaultErrorPolicy; }
    public String getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail; }
    public String getSlackWebhook() { return slackWebhook; }
    public void setSlackWebhook(String slackWebhook) { this.slackWebhook = slackWebhook; }
    public boolean isNotifyOnFailure() { return notifyOnFailure; }
    public void setNotifyOnFailure(boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; }
    public boolean isMaintenanceMode() { return maintenanceMode; }
    public void setMaintenanceMode(boolean maintenanceMode) { this.maintenanceMode = maintenanceMode; }
    public String getMaintenanceMessage() { return maintenanceMessage; }
    public void setMaintenanceMessage(String maintenanceMessage) { this.maintenanceMessage = maintenanceMessage; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
