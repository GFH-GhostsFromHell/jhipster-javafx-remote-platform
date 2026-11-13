package com.example.remoteportal.device;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Additive bootstrap for the opt-in device reports.
 * The existing portal intentionally uses Hibernate ddl-auto=none.
 */
@Component
public class DeviceStatusSchema implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public DeviceStatusSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS device_security_status (
                id VARCHAR(36) PRIMARY KEY,
                owner_username VARCHAR(255) NOT NULL,
                device_id VARCHAR(36) NOT NULL,
                alias VARCHAR(64) NOT NULL,
                operating_system VARCHAR(64) NOT NULL,
                volume VARCHAR(20) NOT NULL,
                encryption_status VARCHAR(24) NOT NULL,
                reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
                CONSTRAINT device_security_owner_device UNIQUE(owner_username, device_id)
            )
            """);
    }
}
