package com.example.remoteportal.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** A consent-based status report; intentionally contains no recovery material. */
@Entity
@Table(name = "device_security_status",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_username", "device_id"}))
public class DeviceStatusRecord {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "owner_username", length = 255, nullable = false)
    private String ownerUsername;

    @Column(name = "device_id", length = 36, nullable = false)
    private String deviceId;

    @Column(length = 64, nullable = false)
    private String alias;

    @Column(length = 64, nullable = false)
    private String operatingSystem;

    @Column(length = 20, nullable = false)
    private String volume;

    @Column(length = 24, nullable = false)
    private String encryptionStatus;

    @Column(nullable = false)
    private Instant reportedAt;

    protected DeviceStatusRecord() { }

    public DeviceStatusRecord(String ownerUsername, String deviceId) {
        this.id = UUID.randomUUID().toString();
        this.ownerUsername = ownerUsername;
        this.deviceId = deviceId;
    }

    public void update(String alias, String operatingSystem, String volume, String encryptionStatus) {
        this.alias = alias;
        this.operatingSystem = operatingSystem;
        this.volume = volume;
        this.encryptionStatus = encryptionStatus;
        this.reportedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getAlias() { return alias; }
    public String getOperatingSystem() { return operatingSystem; }
    public String getVolume() { return volume; }
    public String getEncryptionStatus() { return encryptionStatus; }
    public Instant getReportedAt() { return reportedAt; }
}
