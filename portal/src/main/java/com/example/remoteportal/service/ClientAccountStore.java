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
  public static final List<String> TIERS = List.of("TIER_1", "TIER_2", "TIER_3", "TIER_4");

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
    db.execute("alter table client_account add column if not exists email varchar(255)");
    db.execute("alter table client_account add column if not exists subscription_tier varchar(20) default 'TIER_1'");
    db.execute("alter table client_account add column if not exists avatar_data text");
    db.execute("alter table client_account add column if not exists auth_provider varchar(32) default 'PASSWORD'");
    db.execute("alter table client_account add column if not exists google_subject varchar(255)");
    db.update("update client_account set subscription_tier='TIER_1' where subscription_tier is null or subscription_tier='' ");
    db.update("update client_account set auth_provider='PASSWORD' where auth_provider is null or auth_provider='' ");

    Integer count = db.queryForObject("select count(*) from client_account where lower(username)=lower(?)", Integer.class, seedUsername);
    if (count != null && count == 0) {
      db.update("insert into client_account(username,display_name,password_hash,enabled,email,subscription_tier,avatar_data,auth_provider,google_subject) values(?,?,?,?,?,?,?,?,?)",
          seedUsername, seedDisplayName, encoder.encode(seedPassword), true, null, "TIER_1", null, "PASSWORD", null);
    }
  }

  public UserDetails loadUserByUsername(String username) {
    List<UserDetails> users = db.query(
        "select username,password_hash,enabled from client_account where lower(username)=lower(?) or (email is not null and lower(email)=lower(?)) order by case when lower(username)=lower(?) then 0 else 1 end, username limit 1",
        (rs, row) -> User.withUsername(rs.getString("username"))
            .password(rs.getString("password_hash"))
            .roles("CLIENT")
            .disabled(!rs.getBoolean("enabled"))
            .build(), username, username, username);
    if (users.isEmpty()) throw new UsernameNotFoundException(username);
    return users.getFirst();
  }

  public synchronized String ensureGoogleAccount(String email, String displayName, String googleSubject) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Google account did not provide an email address");
    String username = email.trim().toLowerCase(Locale.ROOT);
    String name = displayName == null || displayName.isBlank() ? username : displayName.trim();

    List<String> existingBySubject = googleSubject == null || googleSubject.isBlank() ? List.of() :
        db.query("select username from client_account where google_subject=?", (rs,row)->rs.getString(1), googleSubject);
    if (!existingBySubject.isEmpty()) {
      String existing = existingBySubject.getFirst();
      db.update("update client_account set display_name=?,email=?,enabled=true,auth_provider='GOOGLE' where username=?", name, username, existing);
      return existing;
    }

    List<String> existing = db.query("select username from client_account where lower(username)=lower(?)", (rs,row)->rs.getString(1), username);
    if (!existing.isEmpty()) {
      String found = existing.getFirst();
      db.update("update client_account set display_name=?,email=?,enabled=true,auth_provider='GOOGLE',google_subject=? where username=?",
          name, username, googleSubject, found);
      return found;
    }

    String unusablePassword = encoder.encode(UUID.randomUUID() + ":google-only:" + UUID.randomUUID());
    db.update("insert into client_account(username,display_name,email,password_hash,enabled,subscription_tier,avatar_data,auth_provider,google_subject) values(?,?,?,?,?,?,?,?,?)",
        username, name, username, unusablePassword, true, "TIER_1", null, "GOOGLE", googleSubject);
    return username;
  }

  public List<Map<String,Object>> list() {
    return db.query("select username,display_name,email,enabled,subscription_tier,auth_provider from client_account order by username", (rs,row) -> {
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("username", rs.getString("username"));
      m.put("displayName", rs.getString("display_name"));
      m.put("email", rs.getString("email"));
      m.put("enabled", rs.getBoolean("enabled"));
      m.put("subscriptionTier", rs.getString("subscription_tier"));
      m.put("authProvider", rs.getString("auth_provider"));
      return m;
    });
  }

  public Map<String,Object> get(String username) {
    return db.query("select username,display_name,email,enabled,subscription_tier,avatar_data,auth_provider from client_account where lower(username)=lower(?)", rs -> {
      if (!rs.next()) throw new UsernameNotFoundException(username);
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("username", rs.getString("username"));
      m.put("displayName", rs.getString("display_name"));
      m.put("email", rs.getString("email"));
      m.put("enabled", rs.getBoolean("enabled"));
      m.put("subscriptionTier", rs.getString("subscription_tier"));
      m.put("avatarData", rs.getString("avatar_data"));
      m.put("authProvider", rs.getString("auth_provider"));
      return m;
    }, username);
  }

  public String tier(String username) {
    List<String> rows = db.query("select subscription_tier from client_account where lower(username)=lower(?)",
        (rs,row) -> rs.getString(1), username);
    if (rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank()) return "TIER_1";
    return rows.getFirst();
  }

  public void upsert(String username, String displayName, String email, String password, boolean enabled, String tier) {
    String normalizedTier = normalizeTier(tier);
    Integer count = db.queryForObject("select count(*) from client_account where lower(username)=lower(?)", Integer.class, username);
    if (count != null && count > 0) {
      if (password != null && !password.isBlank()) {
        db.update("update client_account set display_name=?,email=?,password_hash=?,enabled=?,subscription_tier=?,auth_provider='PASSWORD' where lower(username)=lower(?)",
            displayName, email, encoder.encode(password), enabled, normalizedTier, username);
      } else {
        db.update("update client_account set display_name=?,email=?,enabled=?,subscription_tier=? where lower(username)=lower(?)",
            displayName, email, enabled, normalizedTier, username);
      }
    } else {
      if (password == null || password.isBlank()) throw new IllegalArgumentException("Password required for new client account");
      db.update("insert into client_account(username,display_name,email,password_hash,enabled,subscription_tier,avatar_data,auth_provider,google_subject) values(?,?,?,?,?,?,?,?,?)",
          username, displayName, email, encoder.encode(password), enabled, normalizedTier, null, "PASSWORD", null);
    }
  }

  public void updateProfile(String username, String displayName, String email, String avatarData) {
    if (avatarData != null && avatarData.length() > 2_500_000) throw new IllegalArgumentException("Profile picture is too large");
    int n = db.update("update client_account set display_name=?,email=?,avatar_data=? where lower(username)=lower(?)", displayName, email, avatarData, username);
    if (n == 0) throw new UsernameNotFoundException(username);
  }

  public void changePassword(String username, String currentPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 8) throw new IllegalArgumentException("New password must be at least 8 characters");
    List<String> providers = db.query("select auth_provider from client_account where lower(username)=lower(?)", (rs,row)->rs.getString(1), username);
    if (!providers.isEmpty() && "GOOGLE".equalsIgnoreCase(providers.getFirst())) {
      throw new IllegalArgumentException("This account signs in with Google. Password changes are not available for Google-only accounts.");
    }
    List<String> hashes = db.query("select password_hash from client_account where lower(username)=lower(?)", (rs,row) -> rs.getString(1), username);
    if (hashes.isEmpty()) throw new UsernameNotFoundException(username);
    if (currentPassword == null || !encoder.matches(currentPassword, hashes.getFirst())) throw new IllegalArgumentException("Current password is incorrect");
    db.update("update client_account set password_hash=? where lower(username)=lower(?)", encoder.encode(newPassword), username);
  }

  private static String normalizeTier(String tier) {
    String t = tier == null || tier.isBlank() ? "TIER_1" : tier.toUpperCase(Locale.ROOT);
    if (!TIERS.contains(t)) throw new IllegalArgumentException("Invalid subscription tier");
    return t;
  }
}
