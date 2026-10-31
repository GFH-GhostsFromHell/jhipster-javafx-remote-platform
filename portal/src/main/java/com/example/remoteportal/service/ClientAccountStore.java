package com.example.remoteportal.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClientAccountStore {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final String seedUsername;
  private final String seedPassword;
  private final String seedDisplayName;

  public ClientAccountStore(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${app.client.seed.username:bryn}") String seedUsername,
      @Value("${app.client.seed.password:CHANGE_ME_CLIENT_2026}") String seedPassword,
      @Value("${app.client.seed.display-name:Bryn Flewelling}") String seedDisplayName) {
    this.db = db;
    this.encoder = encoder;
    this.seedUsername = seedUsername;
    this.seedPassword = seedPassword;
    this.seedDisplayName = seedDisplayName;
  }

  @PostConstruct
  void init() {
    db.execute("create table if not exists client_account (username varchar(255) primary key, display_name varchar(255), password_hash varchar(255) not null, enabled boolean not null)");
    Integer count = db.queryForObject("select count(*) from client_account where lower(username)=lower(?)", Integer.class, seedUsername);
    if (count != null && count == 0) {
      db.update("insert into client_account(username,display_name,password_hash,enabled) values(?,?,?,?)",
          seedUsername, seedDisplayName, encoder.encode(seedPassword), true);
    }
  }

  public UserDetails loadUserByUsername(String username) {
    List<UserDetails> users = db.query(
        "select username,password_hash,enabled from client_account where lower(username)=lower(?)",
        (rs, row) -> User.withUsername(rs.getString("username"))
            .password(rs.getString("password_hash"))
            .roles("CLIENT")
            .disabled(!rs.getBoolean("enabled"))
            .build(), username);
    if (users.isEmpty()) throw new UsernameNotFoundException(username);
    return users.getFirst();
  }

  public List<Map<String,Object>> list() {
    return db.query("select username,display_name,enabled from client_account order by username", (rs,row) -> {
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("username", rs.getString("username"));
      m.put("displayName", rs.getString("display_name"));
      m.put("enabled", rs.getBoolean("enabled"));
      return m;
    });
  }

  public Map<String,Object> get(String username) {
    return db.query("select username,display_name,enabled from client_account where lower(username)=lower(?)", rs -> {
      if (!rs.next()) throw new UsernameNotFoundException(username);
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("username", rs.getString("username"));
      m.put("displayName", rs.getString("display_name"));
      m.put("enabled", rs.getBoolean("enabled"));
      return m;
    }, username);
  }

  public void upsert(String username, String displayName, String password, boolean enabled) {
    Integer count = db.queryForObject("select count(*) from client_account where lower(username)=lower(?)", Integer.class, username);
    if (count != null && count > 0) {
      if (password != null && !password.isBlank()) {
        db.update("update client_account set display_name=?,password_hash=?,enabled=? where lower(username)=lower(?)",
            displayName, encoder.encode(password), enabled, username);
      } else {
        db.update("update client_account set display_name=?,enabled=? where lower(username)=lower(?)",
            displayName, enabled, username);
      }
    } else {
      if (password == null || password.isBlank()) throw new IllegalArgumentException("Password required for new client account");
      db.update("insert into client_account(username,display_name,password_hash,enabled) values(?,?,?,?)",
          username, displayName, encoder.encode(password), enabled);
    }
  }
}
