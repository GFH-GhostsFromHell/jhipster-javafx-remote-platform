package com.example.remoteportal.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class EnterpriseAccountService {
  private static final List<String> MEMBER_TIERS = List.of("TIER_1", "TIER_2", "TIER_3");

  private final JdbcTemplate db;
  private final ClientAccountStore accounts;

  public EnterpriseAccountService(JdbcTemplate db, ClientAccountStore accounts) {
    this.db = db;
    this.accounts = accounts;
  }

  @PostConstruct
  void init() {
    db.execute("create table if not exists enterprise_account (owner_username varchar(255) primary key, business_name varchar(255) not null, created_at timestamp not null, updated_at timestamp not null)");
    db.execute("create table if not exists enterprise_member (member_username varchar(255) primary key, owner_username varchar(255) not null, assigned_tier varchar(20) not null, prior_tier varchar(20) not null, role_label varchar(120), added_at timestamp not null, updated_at timestamp not null)");
  }

  public Map<String,Object> dashboard(String ownerUsername) {
    requireEnterpriseOwner(ownerUsername);
    ensureEnterpriseAccount(ownerUsername);
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("ownerUsername", ownerUsername);
    result.put("businessName", businessName(ownerUsername));
    List<Map<String,Object>> members = members(ownerUsername);
    result.put("members", members);
    result.put("seatCount", members.size());
    result.put("tier1Count", countTier(members, "TIER_1"));
    result.put("tier2Count", countTier(members, "TIER_2"));
    result.put("tier3Count", countTier(members, "TIER_3"));
    return result;
  }

  public Map<String,Object> membership(String username) {
    List<Map<String,Object>> rows = db.query(
        "select em.owner_username,em.assigned_tier,em.role_label,ea.business_name from enterprise_member em left join enterprise_account ea on lower(ea.owner_username)=lower(em.owner_username) where lower(em.member_username)=lower(?)",
        (rs,row) -> {
          Map<String,Object> m = new LinkedHashMap<>();
          m.put("managed", true);
          m.put("ownerUsername", rs.getString("owner_username"));
          m.put("assignedTier", rs.getString("assigned_tier"));
          m.put("roleLabel", Objects.toString(rs.getString("role_label"), ""));
          m.put("businessName", Objects.toString(rs.getString("business_name"), rs.getString("owner_username")));
          return m;
        }, username);
    if (rows.isEmpty()) return Map.of("managed", false);
    return rows.getFirst();
  }

  public Map<String,Object> renameBusiness(String ownerUsername, String businessName) {
    requireEnterpriseOwner(ownerUsername);
    ensureEnterpriseAccount(ownerUsername);
    String name = clean(businessName);
    if (name.isBlank()) throw new IllegalArgumentException("Business name is required.");
    if (name.length() > 255) throw new IllegalArgumentException("Business name is too long.");
    db.update("update enterprise_account set business_name=?,updated_at=? where lower(owner_username)=lower(?)",
        name, Timestamp.from(Instant.now()), ownerUsername);
    return dashboard(ownerUsername);
  }

  public Map<String,Object> addMember(String ownerUsername, String memberUsername, String assignedTier, String roleLabel) {
    requireEnterpriseOwner(ownerUsername);
    ensureEnterpriseAccount(ownerUsername);
    String member = clean(memberUsername);
    if (member.isBlank()) throw new IllegalArgumentException("Member username is required.");
    if (ownerUsername.equalsIgnoreCase(member)) throw new IllegalArgumentException("The enterprise owner cannot be added as a managed seat.");
    String tier = normalizeMemberTier(assignedTier);
    String role = normalizeRole(roleLabel);

    Map<String,Object> account;
    try { account = accounts.get(member); }
    catch (Exception ex) { throw new IllegalArgumentException("That user account could not be found."); }
    String currentTier = Objects.toString(account.get("subscriptionTier"), "TIER_1");
    if ("TIER_4".equalsIgnoreCase(currentTier)) throw new IllegalArgumentException("Another Tier 4 business account cannot be added as a managed seat.");

    List<String> existingOwner = db.query(
        "select owner_username from enterprise_member where lower(member_username)=lower(?)",
        (rs,row) -> rs.getString(1), member);
    if (!existingOwner.isEmpty() && !ownerUsername.equalsIgnoreCase(existingOwner.getFirst())) {
      throw new IllegalArgumentException("This user already belongs to another business account.");
    }

    String stripeStatus = firstString("select coalesce(stripe_subscription_status,'') from client_account where lower(username)=lower(?)", member);
    if (List.of("active", "trialing").contains(stripeStatus.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("This user has an active personal paid subscription. Cancel or transfer that subscription before adding the user to an enterprise account.");
    }

    Timestamp now = Timestamp.from(Instant.now());
    if (existingOwner.isEmpty()) {
      db.update("insert into enterprise_member(member_username,owner_username,assigned_tier,prior_tier,role_label,added_at,updated_at) values(?,?,?,?,?,?,?)",
          member, ownerUsername, tier, normalizePriorTier(currentTier), role, now, now);
    } else {
      db.update("update enterprise_member set assigned_tier=?,role_label=?,updated_at=? where lower(member_username)=lower(?) and lower(owner_username)=lower(?)",
          tier, role, now, member, ownerUsername);
    }
    db.update("update client_account set subscription_tier=?,requested_tier=? where lower(username)=lower(?)", tier, tier, member);
    return dashboard(ownerUsername);
  }

  public Map<String,Object> updateMemberTier(String ownerUsername, String memberUsername, String assignedTier) {
    requireEnterpriseOwner(ownerUsername);
    String tier = normalizeMemberTier(assignedTier);
    requireOwnedMember(ownerUsername, memberUsername);
    Timestamp now = Timestamp.from(Instant.now());
    db.update("update enterprise_member set assigned_tier=?,updated_at=? where lower(member_username)=lower(?) and lower(owner_username)=lower(?)",
        tier, now, memberUsername, ownerUsername);
    db.update("update client_account set subscription_tier=?,requested_tier=? where lower(username)=lower(?)", tier, tier, memberUsername);
    return dashboard(ownerUsername);
  }

  public Map<String,Object> updateMemberRole(String ownerUsername, String memberUsername, String roleLabel) {
    requireEnterpriseOwner(ownerUsername);
    requireOwnedMember(ownerUsername, memberUsername);
    db.update("update enterprise_member set role_label=?,updated_at=? where lower(member_username)=lower(?) and lower(owner_username)=lower(?)",
        normalizeRole(roleLabel), Timestamp.from(Instant.now()), memberUsername, ownerUsername);
    return dashboard(ownerUsername);
  }

  public Map<String,Object> removeMember(String ownerUsername, String memberUsername) {
    requireEnterpriseOwner(ownerUsername);
    List<String> prior = db.query(
        "select prior_tier from enterprise_member where lower(member_username)=lower(?) and lower(owner_username)=lower(?)",
        (rs,row) -> rs.getString(1), memberUsername, ownerUsername);
    if (prior.isEmpty()) throw new IllegalArgumentException("That user is not part of this business account.");
    String restoreTier = normalizePriorTier(prior.getFirst());
    db.update("delete from enterprise_member where lower(member_username)=lower(?) and lower(owner_username)=lower(?)", memberUsername, ownerUsername);
    db.update("update client_account set subscription_tier=?,requested_tier=? where lower(username)=lower(?)", restoreTier, restoreTier, memberUsername);
    return dashboard(ownerUsername);
  }

  public boolean isManagedMember(String username) {
    Integer count = db.queryForObject("select count(*) from enterprise_member where lower(member_username)=lower(?)", Integer.class, username);
    return count != null && count > 0;
  }

  public String managingOwner(String username) {
    return firstString("select owner_username from enterprise_member where lower(member_username)=lower(?)", username);
  }

  private List<Map<String,Object>> members(String ownerUsername) {
    return db.query(
        "select em.member_username,em.assigned_tier,em.role_label,em.added_at,ca.display_name,ca.email,ca.enabled,ca.auth_provider from enterprise_member em join client_account ca on lower(ca.username)=lower(em.member_username) where lower(em.owner_username)=lower(?) order by coalesce(ca.display_name,ca.username),ca.username",
        (rs,row) -> {
          Map<String,Object> m = new LinkedHashMap<>();
          m.put("username", rs.getString("member_username"));
          m.put("displayName", Objects.toString(rs.getString("display_name"), rs.getString("member_username")));
          m.put("email", Objects.toString(rs.getString("email"), ""));
          m.put("subscriptionTier", rs.getString("assigned_tier"));
          m.put("roleLabel", Objects.toString(rs.getString("role_label"), ""));
          m.put("enabled", rs.getBoolean("enabled"));
          m.put("authProvider", Objects.toString(rs.getString("auth_provider"), "PASSWORD"));
          Timestamp added = rs.getTimestamp("added_at");
          m.put("addedAt", added == null ? "" : added.toInstant().toString());
          return m;
        }, ownerUsername);
  }

  private void requireEnterpriseOwner(String username) {
    String tier;
    try { tier = accounts.tier(username); }
    catch (Exception ex) { throw new IllegalStateException("Enterprise account could not be verified."); }
    if (!"TIER_4".equalsIgnoreCase(tier)) throw new IllegalStateException("A Tier 4 Business / Enterprise account is required.");
  }

  private void ensureEnterpriseAccount(String ownerUsername) {
    Integer count = db.queryForObject("select count(*) from enterprise_account where lower(owner_username)=lower(?)", Integer.class, ownerUsername);
    if (count != null && count > 0) return;
    Map<String,Object> owner = accounts.get(ownerUsername);
    String display = Objects.toString(owner.get("displayName"), ownerUsername);
    String defaultName = display + " Team";
    Timestamp now = Timestamp.from(Instant.now());
    db.update("insert into enterprise_account(owner_username,business_name,created_at,updated_at) values(?,?,?,?)",
        ownerUsername, defaultName, now, now);
  }

  private String businessName(String ownerUsername) {
    String name = firstString("select business_name from enterprise_account where lower(owner_username)=lower(?)", ownerUsername);
    return name.isBlank() ? ownerUsername : name;
  }

  private void requireOwnedMember(String ownerUsername, String memberUsername) {
    Integer count = db.queryForObject(
        "select count(*) from enterprise_member where lower(owner_username)=lower(?) and lower(member_username)=lower(?)",
        Integer.class, ownerUsername, memberUsername);
    if (count == null || count == 0) throw new IllegalArgumentException("That user is not part of this business account.");
  }

  private String firstString(String sql, String value) {
    List<String> rows = db.query(sql, (rs,row) -> Objects.toString(rs.getString(1), ""), value);
    return rows.isEmpty() ? "" : rows.getFirst();
  }

  private static long countTier(List<Map<String,Object>> members, String tier) {
    return members.stream().filter(m -> tier.equals(m.get("subscriptionTier"))).count();
  }

  private static String normalizeMemberTier(String tier) {
    String t = clean(tier).toUpperCase(Locale.ROOT);
    if (!MEMBER_TIERS.contains(t)) throw new IllegalArgumentException("Enterprise seats can only be assigned Tier 1, Tier 2, or Tier 3.");
    return t;
  }

  private static String normalizePriorTier(String tier) {
    String t = clean(tier).toUpperCase(Locale.ROOT);
    return MEMBER_TIERS.contains(t) ? t : "TIER_1";
  }

  private static String normalizeRole(String role) {
    String value = clean(role);
    if (value.length() > 120) throw new IllegalArgumentException("Role label is too long.");
    return value;
  }

  private static String clean(String value) { return value == null ? "" : value.trim(); }
}
