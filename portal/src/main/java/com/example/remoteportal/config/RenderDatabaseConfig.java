package com.example.remoteportal.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class RenderDatabaseConfig {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "database.url")
  public DataSource renderPostgresDataSource(Environment env) {
    String raw = env.getProperty("database.url");
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("DATABASE_URL is empty");
    }

    URI uri = URI.create(raw);
    String userInfo = uri.getRawUserInfo();
    if (userInfo == null || !userInfo.contains(":")) {
      throw new IllegalStateException("DATABASE_URL does not contain database credentials");
    }

    String[] credentials = userInfo.split(":", 2);
    String username = URLDecoder.decode(credentials[0], StandardCharsets.UTF_8);
    String password = URLDecoder.decode(credentials[1], StandardCharsets.UTF_8);
    int port = uri.getPort() > 0 ? uri.getPort() : 5432;

    HikariDataSource ds = new HikariDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath());
    ds.setUsername(username);
    ds.setPassword(password);
    ds.setMaximumPoolSize(5);
    ds.setMinimumIdle(1);
    ds.setPoolName("RenderPostgresPool");
    return ds;
  }
}
