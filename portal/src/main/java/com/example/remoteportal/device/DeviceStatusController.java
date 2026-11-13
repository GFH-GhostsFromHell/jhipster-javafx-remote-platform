package com.example.remoteportal.device;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User-scoped, opt-in device status reports. No endpoint accepts recovery keys,
 * disk-encryption commands, filesystem paths or decryption tokens.
 */
@RestController
@RequestMapping("/api/device-security")
public class DeviceStatusController {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "ENCRYPTED", "NOT_ENCRYPTED", "IN_PROGRESS", "UNKNOWN", "UNSUPPORTED");
    private final DeviceStatusRepository records;

    public DeviceStatusController(DeviceStatusRepository records) {
        this.records = records;
    }

    public record ReportRequest(String deviceId, String alias, String operatingSystem,
                                String volume, String encryptionStatus) { }

    @GetMapping("/devices")
    public List<DeviceStatusRecord> list(Authentication auth) {
        return records.findAllByOwnerUsernameOrderByReportedAtDesc(requireOwner(auth));
    }

    @PostMapping("/devices")
    public DeviceStatusRecord report(Authentication auth, @RequestBody ReportRequest request) {
        String owner = requireOwner(auth);
        if (request == null || !isUuid(request.deviceId())
            || !valid(request.alias(), 64) || !valid(request.operatingSystem(), 64)
            || !valid(request.volume(), 20)
            || !ALLOWED_STATUSES.contains(request.encryptionStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid device status report. No credentials or recovery tokens are accepted.");
        }
        DeviceStatusRecord report = records.findByOwnerUsernameAndDeviceId(owner, request.deviceId())
            .orElseGet(() -> new DeviceStatusRecord(owner, request.deviceId()));
        report.update(request.alias().trim(), request.operatingSystem().trim(),
                      request.volume().trim(), request.encryptionStatus());
        return records.save(report);
    }

    private static String requireOwner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
            || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
        }
        return auth.getName();
    }

    private static boolean valid(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
            && value.chars().noneMatch(c -> Character.isISOControl(c));
    }
    private static boolean isUuid(String candidate) {
        if (candidate == null || candidate.length() != 36) return false;
        try { return UUID.fromString(candidate).toString().equalsIgnoreCase(candidate); }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
