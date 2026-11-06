package com.example.remoteportal.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AccountLifecycleService {
  private static final Logger LOG = LoggerFactory.getLogger(AccountLifecycleService.class);
  private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,40}");
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final int MAX_ATTEMPTS = 8;

  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final MailDeliveryService mail;
  private final SecureRandom random = new SecureRandom();

  public AccountLifecycleService(JdbcTemplate db, PasswordEncoder encoder, MailDeliveryService mail, ClientAccountStore accounts) {
    this.db = db;
    this.encoder = encoder;
    this.mail = mail;
  }

  @PostConstruct
  void init() {
    db.execute("alter table client_account add column if not exists email_verified boolean default false");
    db.execute("alter table client_account add column if not exists requested_tier varchar(20) default 'TIER_1'");
    db.execute("create table if not exists account_action_code (purpose varchar(32) not null, lookup_key varchar(255) not null, username varchar(255), code_hash varchar(255) not null, expires_at timestamp not null, attempts integer not null default 0, created_at timestamp not null, primary key(purpose,lookup_key))");
    db.update("update client_account set requested_tier=subscription_tier where requested_tier is null or requested_tier='' ");
    db.update("update client_account set email_verified=true where upper(auth_provider)='GOOGLE'");
  }

  public boolean mailConfigured() { return mail.configured(); }

  public Map<String,Object> beginRegistration(String username, String displayName, String email, String password, String requestedTier) {
    ensureMail();
    String u = clean(username);
    String e = clean(email).toLowerCase(Locale.ROOT);
    String name = clean(displayName);
    String requested = TierCheckoutService.normalize(requestedTier);
    validateRegistration(u, e, password);
    LOG.info("Registration request passed input validation");
    if (name.isBlank()) name = u;

    List<Map<String,Object>> existing = db.query(
        "select username,email,enabled,auth_provider from client_account where lower(username)=lower(?)",
        (rs,row) -> Map.of(
            "username", rs.getString("username"),
            "email", Objects.toString(rs.getString("email"), ""),
            "enabled", rs.getBoolean("enabled"),
            "provider", Objects.toString(rs.getString("auth_provider"), "PASSWORD")), u);

    Integer emailUsed = db.queryForObject(
        "select count(*) from client_account where email is not null and lower(email)=lower(?) and lower(username)<>lower(?)",
        Integer.class, e, u);
    if (emailUsed != null && emailUsed > 0) throw new IllegalArgumentException("This email address already has an Electrical Workbench account. Use Forgot password to recover it, or use a different email.");

    String hash = encoder.encode(password);
    if (!existing.isEmpty()) {
      Map<String,Object> row = existing.getFirst();
      boolean enabled = Boolean.TRUE.equals(row.get("enabled"));
      String provider = Objects.toString(row.get("provider"), "PASSWORD");
      String oldEmail = Objects.toString(row.get("email"), "");
      if (enabled || !"PASSWORD".equalsIgnoreCase(provider) || (!oldEmail.isBlank() && !oldEmail.equalsIgnoreCase(e))) {
        throw new IllegalArgumentException("That username is already in use. Choose a different username, or use Forgot password if it belongs to you.");
      }
      Integer pending = db.queryForObject(
          "select count(*) from account_action_code where purpose='REGISTER' and lookup_key=?",
          Integer.class, u.toLowerCase(Locale.ROOT));
      if (pending == null || pending == 0) throw new IllegalArgumentException("That username is already in use. Choose a different username, or use Forgot password if it belongs to you.");
      db.update("update client_account set display_name=?,email=?,password_hash=?,enabled=false,email_verified=false,subscription_tier='TIER_1',requested_tier=?,auth_provider='PASSWORD' where lower(username)=lower(?)",
          name, e, hash, requested, u);
    } else {
      db.update("insert into client_account(username,display_name,email,password_hash,enabled,subscription_tier,avatar_data,auth_provider,google_subject,email_verified,requested_tier) values(?,?,?,?,?,?,?,?,?,?,?)",
          u, name, e, hash, false, "TIER_1", null, "PASSWORD", null, false, requested);
    }

    String code = newCode();
    saveCode("REGISTER", u.toLowerCase(Locale.ROOT), u, code);
    LOG.info("Registration record and verification code saved; handing message to SMTP provider");
    mail.sendVerificationCode(e, name, code);
    LOG.info("Registration verification message accepted by SMTP transport");
    return Map.of("verificationRequired", true, "username", u, "email", maskEmail(e), "requestedTier", requested, "expiresMinutes", 15);
  }

  public Map<String,Object> verifyRegistration(String username, String code) {
    String u = clean(username);
    if (u.isBlank()) throw new IllegalArgumentException("Username is required.");
    verifyCode("REGISTER", u.toLowerCase(Locale.ROOT), code);
    List<String> requested = db.query("select requested_tier from client_account where lower(username)=lower(?)", (rs,row)->rs.getString(1), u);
    if (requested.isEmpty()) throw new IllegalArgumentException("Registration could not be found.");
    db.update("update client_account set enabled=true,email_verified=true where lower(username)=lower(?)", u);
    deleteCode("REGISTER", u.toLowerCase(Locale.ROOT));
    return Map.of("verified", true, "username", u, "subscriptionTier", "TIER_1", "requestedTier", TierCheckoutService.normalize(requested.getFirst()));
  }

  public void sendUsernameReminder(String email) {
    ensureMail();
    String e = clean(email).toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(e).matches()) throw new IllegalArgumentException("Enter a valid email address, such as name@example.com.");
    List<String> usernames = db.query(
        "select username from client_account where email is not null and lower(email)=lower(?) and enabled=true order by username",
        (rs,row)->rs.getString(1), e);
    if (!usernames.isEmpty()) mail.sendUsernameReminder(e, String.join("\n", usernames));
  }

  public void beginPasswordReset(String account) {
    ensureMail();
    String lookup = clean(account);
    if (lookup.isBlank()) throw new IllegalArgumentException("Enter your username or email address.");
    List<RecoveryTarget> targets = db.query(
        "select username,email,auth_provider from client_account where (enabled=true or (coalesce(email_verified,false)=false and exists (select 1 from account_action_code ac where ac.purpose='REGISTER' and lower(ac.username)=lower(client_account.username)))) and (lower(username)=lower(?) or (email is not null and lower(email)=lower(?))) order by case when lower(username)=lower(?) then 0 else 1 end, username",
        (rs,row)->new RecoveryTarget(rs.getString("username"), rs.getString("email"), rs.getString("auth_provider")), lookup, lookup, lookup);
    if (targets.isEmpty()) return;
    RecoveryTarget target = targets.getFirst();
    if (target.email() == null || target.email().isBlank() || "GOOGLE".equalsIgnoreCase(target.provider())) return;
    String code = newCode();
    saveCode("PASSWORD_RESET", target.username().toLowerCase(Locale.ROOT), target.username(), code);
    mail.sendPasswordResetCode(target.email(), target.username(), code);
  }

  public void completePasswordReset(String username, String code, String newPassword) {
    String lookup = clean(username);
    if (lookup.isBlank()) throw new IllegalArgumentException("Username or email is required.");
    if (newPassword == null || newPassword.length() < 8) throw new IllegalArgumentException("New password must be at least 8 characters.");
    // An emailed reset code proves control of the address, including for pending
    // registrations. Never reactivate an admin-disabled, already verified account.
    List<RecoveryTarget> targets = db.query(
        "select username,email,auth_provider from client_account where (enabled=true or (coalesce(email_verified,false)=false and exists (select 1 from account_action_code ac where ac.purpose='REGISTER' and lower(ac.username)=lower(client_account.username)))) and (lower(username)=lower(?) or (email is not null and lower(email)=lower(?))) order by case when lower(username)=lower(?) then 0 else 1 end, username",
        (rs,row)->new RecoveryTarget(rs.getString("username"), rs.getString("email"), rs.getString("auth_provider")), lookup, lookup, lookup);
    if (targets.isEmpty()) throw new IllegalArgumentException("Account could not be found.");
    RecoveryTarget target = targets.getFirst();
    if ("GOOGLE".equalsIgnoreCase(target.provider())) throw new IllegalArgumentException("This account uses Google sign-in.");
    String u = target.username();
    verifyCode("PASSWORD_RESET", u.toLowerCase(Locale.ROOT), code);
    db.update("update client_account set password_hash=?,enabled=true,email_verified=true where lower(username)=lower(?)",
        encoder.encode(newPassword), u);
    deleteCode("PASSWORD_RESET", u.toLowerCase(Locale.ROOT));
    deleteCode("REGISTER", u.toLowerCase(Locale.ROOT));
  }

  private void validateRegistration(String username, String email, String password) {
    if (!USERNAME.matcher(username).matches()) throw new IllegalArgumentException("Username must be 3–40 characters. Use only letters, numbers, periods, hyphens or underscores.");
    if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Enter a valid email address, such as name@example.com.");
    if (password == null || password.length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters.");
  }

  private void saveCode(String purpose, String key, String username, String code) {
    Timestamp now = Timestamp.from(Instant.now());
    Timestamp expires = Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES));
    int n = db.update("update account_action_code set username=?,code_hash=?,expires_at=?,attempts=0,created_at=? where purpose=? and lookup_key=?",
        username, encoder.encode(code), expires, now, purpose, key);
    if (n == 0) db.update("insert into account_action_code(purpose,lookup_key,username,code_hash,expires_at,attempts,created_at) values(?,?,?,?,?,?,?)",
        purpose, key, username, encoder.encode(code), expires, 0, now);
  }

  private void verifyCode(String purpose, String key, String code) {
    if (code == null || !code.matches("\\d{6}")) throw new IllegalArgumentException("Enter the 6-digit code from your email.");
    List<CodeRow> rows = db.query("select code_hash,expires_at,attempts from account_action_code where purpose=? and lookup_key=?",
        (rs,row)->new CodeRow(rs.getString("code_hash"), rs.getTimestamp("expires_at"), rs.getInt("attempts")), purpose, key);
    if (rows.isEmpty()) throw new IllegalArgumentException("The verification code is incorrect, expired, or has been replaced. Request a new code and try again.");
    CodeRow row = rows.getFirst();
    if (row.attempts() >= MAX_ATTEMPTS || row.expiresAt() == null || Instant.now().isAfter(row.expiresAt().toInstant())) {
      deleteCode(purpose, key);
      throw new IllegalArgumentException("The verification code is incorrect, expired, or has been replaced. Request a new code and try again.");
    }
    if (!encoder.matches(code, row.hash())) {
      db.update("update account_action_code set attempts=attempts+1 where purpose=? and lookup_key=?", purpose, key);
      throw new IllegalArgumentException("The verification code is incorrect, expired, or has been replaced. Request a new code and try again.");
    }
  }

  private void deleteCode(String purpose, String key) {
    db.update("delete from account_action_code where purpose=? and lookup_key=?", purpose, key);
  }

  private String newCode() {
    return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
  }

  private void ensureMail() {
    if (!mail.configured()) throw new IllegalStateException("Email verification and recovery are not configured yet.");
  }

  private static String maskEmail(String email) {
    int at = email.indexOf('@');
    if (at <= 1) return "***" + email.substring(Math.max(0, at));
    return email.substring(0, 1) + "***" + email.substring(at);
  }

  private static String clean(String value) { return value == null ? "" : value.trim(); }

  private record CodeRow(String hash, Timestamp expiresAt, int attempts) {}
  private record RecoveryTarget(String username, String email, String provider) {}
}
