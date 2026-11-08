package com.example.remoteportal.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public, non-sensitive build identity for deployment drift detection.
 * Render supplies RENDER_GIT_COMMIT to running services; local launches
 * intentionally report "unknown" instead of inventing a commit.
 */
@RestController
public class ProductionBuildResource {
    private final String commit;
    private final String service;

    public ProductionBuildResource(
            @Value("${RENDER_GIT_COMMIT:${APP_GIT_SHA:}}") String commit,
            @Value("${RENDER_SERVICE_NAME:remote-javafx-portal}") String service) {
        this.commit = normalize(commit);
        this.service = service == null || service.isBlank() ? "remote-javafx-portal" : service;
    }

    @GetMapping("/api/public/build-info")
    public Map<String,Object> buildInfo() {
        Map<String,Object> info = new LinkedHashMap<>();
        info.put("status","UP");
        info.put("service", service);
        info.put("commit", commit);
        info.put("commitAvailable", !"unknown".equals(commit));
        info.put("referenceProfileId","OESC_ON_2024");
        info.put("numericDatasetVerified",false);
        info.put("codeComplianceVerified",false);
        return info;
    }

    static String normalize(String raw) {
        if (raw == null) return "unknown";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return value.matches("[0-9a-f]{40}") ? value : "unknown";
    }
}
