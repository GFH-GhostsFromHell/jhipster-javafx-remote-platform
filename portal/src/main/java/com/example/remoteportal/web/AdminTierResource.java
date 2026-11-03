package com.example.remoteportal.web;

import com.example.remoteportal.service.ClientAccountStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminTierResource {
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
    int updated = db.update(
        "update client_account set subscription_tier=?, requested_tier=? where lower(username)=lower(?)",
        tier, tier, username);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    Map<String,Object> result = new LinkedHashMap<>();
    result.put("username", username);
    result.put("subscriptionTier", tier);
    result.put("updatedBy", authentication == null ? "admin" : authentication.getName());
    return result;
  }

  private static String normalizeTier(String tier) {
    String normalized = tier == null ? "" : tier.trim().toUpperCase(Locale.ROOT);
    if (!ClientAccountStore.TIERS.contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription tier");
    }
    return normalized;
  }
}
