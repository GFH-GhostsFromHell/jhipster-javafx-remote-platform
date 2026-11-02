package com.example.remoteportal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TierCheckoutService {
  private final String tier2Url;
  private final String tier3Url;

  public TierCheckoutService(
      @Value("${TIER_2_CHECKOUT_URL:}") String tier2Url,
      @Value("${TIER_3_CHECKOUT_URL:}") String tier3Url) {
    this.tier2Url = clean(tier2Url);
    this.tier3Url = clean(tier3Url);
  }

  public List<Map<String,Object>> options() {
    return List.of(
        option("TIER_1", "Tier 1", "Core electrical tools", true, ""),
        option("TIER_2", "Tier 2", "Adds PLC/remote tools and Engineering Power Systems", !tier2Url.isBlank(), tier2Url),
        option("TIER_3", "Tier 3", "Adds advanced analytics and full engineering mode", !tier3Url.isBlank(), tier3Url)
    );
  }

  public Optional<String> checkoutUrl(String tier) {
    String t = normalize(tier);
    if ("TIER_2".equals(t) && !tier2Url.isBlank()) return Optional.of(tier2Url);
    if ("TIER_3".equals(t) && !tier3Url.isBlank()) return Optional.of(tier3Url);
    return Optional.empty();
  }

  public boolean configured(String tier) {
    return checkoutUrl(tier).isPresent();
  }

  private static Map<String,Object> option(String id, String name, String description, boolean checkoutConfigured, String checkoutUrl) {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("name", name);
    m.put("description", description);
    m.put("checkoutConfigured", checkoutConfigured);
    if (!checkoutUrl.isBlank()) m.put("checkoutUrl", checkoutUrl);
    return m;
  }

  public static String normalize(String tier) {
    String t = tier == null || tier.isBlank() ? "TIER_1" : tier.trim().toUpperCase(Locale.ROOT);
    if (!ClientAccountStore.TIERS.contains(t)) throw new IllegalArgumentException("Invalid subscription tier");
    return t;
  }

  private static String clean(String s) {
    return s == null ? "" : s.trim();
  }
}
