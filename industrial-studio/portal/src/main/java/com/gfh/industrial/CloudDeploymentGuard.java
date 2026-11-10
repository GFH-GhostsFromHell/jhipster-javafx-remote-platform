package com.gfh.industrial;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prevents accidentally publishing the experimental OPC UA bridge or an ephemeral H2
 * store in the cloud. This guard is explicitly enabled only in the Render image.
 * Not a substitute for network isolation, production IAM or a security assessment.
 */
@Component
public final class CloudDeploymentGuard {
    private final boolean cloud;
    private final String source;
    private final String dbUrl;
    private final String advertisedUrl;
    private final String bind;
    private final String adminPassword;
    private final String viewerPassword;

    public CloudDeploymentGuard(
            @Value("${STUDIO_CLOUD_CONFIG_ONLY:false}") boolean cloud,
            @Value("${STUDIO_SOURCE_MODE:SIMULATION}") String source,
            @Value("${STUDIO_DB_URL:}") String dbUrl,
            @Value("${STUDIO_CLIENT_BASE_URL:}") String advertisedUrl,
            @Value("${STUDIO_BIND:127.0.0.1}") String bind,
            @Value("${STUDIO_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${STUDIO_VIEWER_PASSWORD:}") String viewerPassword) {
        this.cloud = cloud;
        this.source = source;
        this.dbUrl = dbUrl;
        this.advertisedUrl = advertisedUrl;
        this.bind = bind;
        this.adminPassword = adminPassword;
        this.viewerPassword = viewerPassword;
    }

    @PostConstruct
    public void check() {
        if (!cloud) return;
        if (!"SIMULATION".equalsIgnoreCase(source.trim())) {
            throw new IllegalStateException("Cloud configurator cannot enable direct OPC UA or PLC access");
        }
        if (!dbUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Cloud configurator requires persistent PostgreSQL, not local H2");
        }
        if (!advertisedUrl.startsWith("https://") || advertisedUrl.contains("@")) {
            throw new IllegalStateException("Cloud client URL must use HTTPS and contain no credentials");
        }
        if (!"0.0.0.0".equals(bind)) {
            throw new IllegalStateException("Cloud HTTP listener must bind to 0.0.0.0 behind Render TLS proxy");
        }
        if (adminPassword.length() < 20 || viewerPassword.length() < 20
                || adminPassword.equals(viewerPassword)) {
            throw new IllegalStateException("Cloud credentials must be different and each have 20+ characters");
        }
    }
}
