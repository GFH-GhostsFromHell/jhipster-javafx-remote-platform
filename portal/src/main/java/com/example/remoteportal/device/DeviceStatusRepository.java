package com.example.remoteportal.device;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatusRecord, String> {
    List<DeviceStatusRecord> findAllByOwnerUsernameOrderByReportedAtDesc(String ownerUsername);
    Optional<DeviceStatusRecord> findByOwnerUsernameAndDeviceId(String ownerUsername, String deviceId);
}
