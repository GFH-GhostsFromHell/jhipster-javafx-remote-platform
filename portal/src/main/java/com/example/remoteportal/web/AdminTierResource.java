package com.example.remoteportal.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminTierResource {
  private static final List<String> TIERS = List.of("TIER_1", "TIER_2", "TIER_3", "TIER_4");
  private final JdbcTemplate db;

  public AdminTierResource(JdbcTemplate db) {
    this.db = db;
  }

  public record TierUpdate(String subscriptionTier) {}

  @PutMapping("/users/{username}/tier")
  public Map<String,Object> updateTier(
      @PathVariable String username,
      @RequestBody TierUpdate update,
      Authentication authentication) {
    String tier = normalizeTier(update == null ? null : update.subscriptionTier());
    Integer exists = db.queryForObject("select count(*) from client_account where lower(username)=lower(?)", Integer.class, username);
    if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

    Integer ownedSeats = db.queryForObject("select count(*) from enterprise_member where lower(owner_username)=lower(?)", Integer.class, username);
    if (!"TIER_4".equals(tier) && ownedSeats != null && ownedSeats > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Remove all managed business seats before downgrading this Tier 4 account.");
    }

    Integer managedSeat = db.queryForObject("select count(*) from enterprise_member where lower(member_username)=lower(?)", Integer.class, username);
    if ("TIER_4".equals(tier)) {
      if (managedSeat != null && managedSeat > 0) {
        db.update("delete from enterprise_member where lower(member_username)=lower(?)", username);
      }
    } else if (managedSeat != null && managedSeat > 0) {
      db.update("update enterprise_member set assigned_tier=?,updated_at=? where lower(member_username)=lower(?)",
          tier, Timestamp.from(Instant.now()), username);
    }

    int updated = db.update(
        "update client_account set subscription_tier=?, requested_tier=? where lower(username)=lower(?)",
        tier, tier, username);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

    if (!"TIER_4".equals(tier)) db.update("delete from enterprise_account where lower(owner_username)=lower(?)", username);

    Map<String,Object> result = new LinkedHashMap<>();
    result.put("username", username);
    result.put("subscriptionTier", tier);
    result.put("enterpriseOwner", "TIER_4".equals(tier));
    result.put("updatedBy", authentication == null ? "admin" : authentication.getName());
    return result;
  }

  private static String normalizeTier(String tier) {
    String normalized = tier == null ? "" : tier.trim().toUpperCase(Locale.ROOT);
    if (!TIERS.contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription tier");
    }
    return normalized;
  }
}
