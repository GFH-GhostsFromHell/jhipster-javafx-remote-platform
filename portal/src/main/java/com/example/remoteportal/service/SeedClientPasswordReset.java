package com.example.remoteportal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Keeps the seeded client test account synchronized with app.client.seed.password
 * when the application starts.
 */
@Component
public class SeedClientPasswordReset implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final String seedUsername;
  private final String seedPassword;

  public SeedClientPasswordReset(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${app.client.seed.username:bryn}") String seedUsername,
      @Value("${app.client.seed.password:bryn}") String seedPassword) {
    this.db = db;
    this.encoder = encoder;
    this.seedUsername = seedUsername;
    this.seedPassword = seedPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    db.update(
        "update client_account set password_hash=?, enabled=true, auth_provider='PASSWORD' where lower(username)=lower(?)",
        encoder.encode(seedPassword), seedUsername);
  }
}
