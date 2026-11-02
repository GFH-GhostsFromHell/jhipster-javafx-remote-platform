package com.example.remoteportal.service;

import com.stripe.StripeClient;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.billingportal.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class TierCheckoutService {
  private final JdbcTemplate db;
  private final ClientAccountStore accounts;
  private final StripeClient stripe;
  private final String webhookSecret;
  private final String tier2PriceId;
  private final String tier3PriceId;
  private final String baseUrl;
  private final boolean automaticTax;

  public TierCheckoutService(
      JdbcTemplate db,
      ClientAccountStore accounts,
      @Value("${STRIPE_SECRET_KEY:}") String secretKey,
      @Value("${STRIPE_WEBHOOK_SECRET:}") String webhookSecret,
      @Value("${TIER_2_STRIPE_PRICE_ID:}") String tier2PriceId,
      @Value("${TIER_3_STRIPE_PRICE_ID:}") String tier3PriceId,
      @Value("${APP_BASE_URL:https://remote-javafx-portal.onrender.com}") String baseUrl,
      @Value("${STRIPE_AUTOMATIC_TAX:false}") boolean automaticTax) {
    this.db = db;
    this.accounts = accounts;
    String key = clean(secretKey);
    this.stripe = key.isBlank() ? null : new StripeClient(key);
    this.webhookSecret = clean(webhookSecret);
    this.tier2PriceId = clean(tier2PriceId);
    this.tier3PriceId = clean(tier3PriceId);
    this.baseUrl = clean(baseUrl).replaceAll("/+$", "");
    this.automaticTax = automaticTax;
  }

  @PostConstruct
  void init() {
    db.execute("alter table client_account add column if not exists stripe_customer_id varchar(255)");
    db.execute("alter table client_account add column if not exists stripe_subscription_id varchar(255)");
    db.execute("alter table client_account add column if not exists stripe_subscription_status varchar(40)");
    db.execute("alter table client_account add column if not exists stripe_subscription_tier varchar(20)");
    db.execute("alter table client_account add column if not exists stripe_updated_at timestamp");
    db.execute("create table if not exists stripe_event_log (event_id varchar(255) primary key, event_type varchar(255), processed_at timestamp not null)");
  }

  public List<Map<String,Object>> options() {
    return List.of(
        option("TIER_1", "Tier 1", "Core electrical tools", true),
        option("TIER_2", "Tier 2", "Adds PLC/remote tools and Engineering Power Systems", configured("TIER_2")),
        option("TIER_3", "Tier 3", "Adds advanced analytics and full engineering mode", configured("TIER_3"))
    );
  }

  public Map<String,Object> profileOptions(String username) {
    BillingState state = billingState(username);
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("currentTier", accounts.tier(username));
    result.put("tiers", options());
    result.put("stripeConfigured", stripe != null);
    result.put("webhookConfigured", stripe != null && !webhookSecret.isBlank());
    result.put("hasCustomer", !state.customerId().isBlank());
    result.put("hasSubscription", !state.subscriptionId().isBlank() && !isTerminalStatus(state.status()));
    result.put("subscriptionStatus", state.status());
    result.put("subscriptionTier", state.tier());
    result.put("billingPortalAvailable", stripe != null && !state.customerId().isBlank());
    return result;
  }

  public boolean configured(String tier) {
    String t = normalize(tier);
    if ("TIER_1".equals(t)) return true;
    return stripe != null && !priceId(t).isBlank();
  }

  public boolean webhookConfigured() {
    return stripe != null && !webhookSecret.isBlank();
  }

  public String createCheckoutSession(String username, String tier) {
    String t = normalize(tier);
    if ("TIER_1".equals(t)) return baseUrl + "/portal.html";
    if (!configured(t)) throw new IllegalStateException("Stripe checkout is not configured for " + t.replace('_', ' ') + ".");

    BillingState billing = billingState(username);
    if (!billing.subscriptionId().isBlank() && !isTerminalStatus(billing.status())) {
      return createBillingPortalSession(username);
    }

    Map<String,Object> account = accounts.get(username);
    String email = clean(Objects.toString(account.get("email"), ""));
    String price = priceId(t);

    try {
      com.stripe.param.checkout.SessionCreateParams.Builder builder = com.stripe.param.checkout.SessionCreateParams.builder()
          .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
          .setSuccessUrl(baseUrl + "/portal.html?billing=success&session_id={CHECKOUT_SESSION_ID}")
          .setCancelUrl(baseUrl + "/portal.html?billing=cancel")
          .setClientReferenceId(username)
          .setAllowPromotionCodes(true)
          .putMetadata("username", username)
          .putMetadata("tier", t)
          .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
              .setPrice(price)
              .setQuantity(1L)
              .build())
          .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
              .putMetadata("username", username)
              .putMetadata("tier", t)
              .build());

      if (automaticTax) {
        builder.setAutomaticTax(com.stripe.param.checkout.SessionCreateParams.AutomaticTax.builder().setEnabled(true).build());
      }
      if (!billing.customerId().isBlank()) builder.setCustomer(billing.customerId());
      else if (!email.isBlank()) builder.setCustomerEmail(email);

      Session session = stripe.v1().checkout().sessions().create(builder.build());
      if (session.getUrl() == null || session.getUrl().isBlank()) throw new IllegalStateException("Stripe did not return a checkout URL.");
      return session.getUrl();
    } catch (Exception ex) {
      throw new IllegalStateException("Stripe checkout could not be created: " + safeMessage(ex), ex);
    }
  }

  public String createBillingPortalSession(String username) {
    if (stripe == null) throw new IllegalStateException("Stripe billing is not configured.");
    BillingState state = billingState(username);
    if (state.customerId().isBlank()) throw new IllegalStateException("This account does not have a Stripe billing profile yet.");
    try {
      SessionCreateParams params = SessionCreateParams.builder()
          .setCustomer(state.customerId())
          .setReturnUrl(baseUrl + "/portal.html?billing=return")
          .build();
      com.stripe.model.billingportal.Session session = stripe.v1().billingPortal().sessions().create(params);
      if (session.getUrl() == null || session.getUrl().isBlank()) throw new IllegalStateException("Stripe did not return a billing portal URL.");
      return session.getUrl();
    } catch (Exception ex) {
      throw new IllegalStateException("Stripe billing portal could not be created: " + safeMessage(ex), ex);
    }
  }

  @Transactional
  public void handleWebhook(String payload, String stripeSignature) throws Exception {
    if (!webhookConfigured()) throw new IllegalStateException("Stripe webhook verification is not configured.");
    Event event = Webhook.constructEvent(payload, stripeSignature, webhookSecret);
    Integer seen = db.queryForObject("select count(*) from stripe_event_log where event_id=?", Integer.class, event.getId());
    if (seen != null && seen > 0) return;

    StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
    if (object != null) {
      switch (event.getType()) {
        case "checkout.session.completed" -> {
          if (object instanceof Session session) handleCheckoutCompleted(session);
        }
        case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" -> {
          if (object instanceof Subscription subscription) handleSubscription(subscription);
        }
        default -> { }
      }
    }

    db.update("insert into stripe_event_log(event_id,event_type,processed_at) values(?,?,?)",
        event.getId(), event.getType(), Timestamp.from(Instant.now()));
  }

  private void handleCheckoutCompleted(Session session) {
    if (!"subscription".equalsIgnoreCase(session.getMode())) return;
    String username = firstNonBlank(session.getClientReferenceId(), metadata(session.getMetadata(), "username"));
    String tier = normalizeNullable(metadata(session.getMetadata(), "tier"));
    if (username.isBlank() || tier.isBlank()) return;

    String customerId = clean(session.getCustomer());
    String subscriptionId = clean(session.getSubscription());
    boolean paid = "paid".equalsIgnoreCase(session.getPaymentStatus()) || "no_payment_required".equalsIgnoreCase(session.getPaymentStatus());
    syncState(username, customerId, subscriptionId, tier, paid ? "active" : "pending", paid ? Access.GRANT : Access.KEEP);
  }

  private void handleSubscription(Subscription subscription) {
    String customerId = clean(subscription.getCustomer());
    String subscriptionId = clean(subscription.getId());
    String username = firstNonBlank(metadata(subscription.getMetadata(), "username"), findUsername(customerId, subscriptionId));
    if (username.isBlank()) return;

    String tier = tierFromSubscription(subscription);
    if (tier.isBlank()) tier = firstNonBlank(metadata(subscription.getMetadata(), "tier"), billingState(username).tier());
    if (tier.isBlank()) tier = "TIER_1";
    String status = clean(subscription.getStatus()).toLowerCase(Locale.ROOT);

    Access access = switch (status) {
      case "active", "trialing" -> Access.GRANT;
      case "canceled", "unpaid", "incomplete_expired", "paused" -> Access.REVOKE;
      default -> Access.KEEP;
    };
    syncState(username, customerId, subscriptionId, tier, status, access);
  }

  private void syncState(String username, String customerId, String subscriptionId, String tier, String status, Access access) {
    String t = normalize(tier);
    Timestamp now = Timestamp.from(Instant.now());
    String customer = clean(customerId);
    String subscription = clean(subscriptionId);
    String s = clean(status);

    if (access == Access.GRANT) {
      db.update("update client_account set stripe_customer_id=coalesce(nullif(?,''),stripe_customer_id),stripe_subscription_id=coalesce(nullif(?,''),stripe_subscription_id),stripe_subscription_status=?,stripe_subscription_tier=?,stripe_updated_at=?,subscription_tier=?,requested_tier=? where lower(username)=lower(?)",
          customer, subscription, s, t, now, t, t, username);
    } else if (access == Access.REVOKE) {
      db.update("update client_account set stripe_customer_id=coalesce(nullif(?,''),stripe_customer_id),stripe_subscription_id=coalesce(nullif(?,''),stripe_subscription_id),stripe_subscription_status=?,stripe_subscription_tier=?,stripe_updated_at=?,subscription_tier='TIER_1' where lower(username)=lower(?)",
          customer, subscription, s, t, now, username);
    } else {
      db.update("update client_account set stripe_customer_id=coalesce(nullif(?,''),stripe_customer_id),stripe_subscription_id=coalesce(nullif(?,''),stripe_subscription_id),stripe_subscription_status=?,stripe_subscription_tier=?,stripe_updated_at=? where lower(username)=lower(?)",
          customer, subscription, s, t, now, username);
    }
  }

  private BillingState billingState(String username) {
    List<BillingState> rows = db.query(
        "select coalesce(stripe_customer_id,''),coalesce(stripe_subscription_id,''),coalesce(stripe_subscription_status,''),coalesce(stripe_subscription_tier,'') from client_account where lower(username)=lower(?)",
        (rs,row) -> new BillingState(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), username);
    return rows.isEmpty() ? new BillingState("", "", "", "") : rows.getFirst();
  }

  private String findUsername(String customerId, String subscriptionId) {
    if (!subscriptionId.isBlank()) {
      List<String> bySubscription = db.query("select username from client_account where stripe_subscription_id=?", (rs,row)->rs.getString(1), subscriptionId);
      if (!bySubscription.isEmpty()) return bySubscription.getFirst();
    }
    if (!customerId.isBlank()) {
      List<String> byCustomer = db.query("select username from client_account where stripe_customer_id=?", (rs,row)->rs.getString(1), customerId);
      if (!byCustomer.isEmpty()) return byCustomer.getFirst();
    }
    return "";
  }

  private String tierFromSubscription(Subscription subscription) {
    try {
      if (subscription.getItems() == null || subscription.getItems().getData() == null) return "";
      for (var item : subscription.getItems().getData()) {
        if (item.getPrice() == null) continue;
        String id = clean(item.getPrice().getId());
        if (!tier2PriceId.isBlank() && tier2PriceId.equals(id)) return "TIER_2";
        if (!tier3PriceId.isBlank() && tier3PriceId.equals(id)) return "TIER_3";
      }
    } catch (Exception ignored) { }
    return "";
  }

  private String priceId(String tier) {
    return switch (normalize(tier)) {
      case "TIER_2" -> tier2PriceId;
      case "TIER_3" -> tier3PriceId;
      default -> "";
    };
  }

  private static Map<String,Object> option(String id, String name, String description, boolean checkoutConfigured) {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("name", name);
    m.put("description", description);
    m.put("checkoutConfigured", checkoutConfigured);
    m.put("provider", "stripe");
    return m;
  }

  public static String normalize(String tier) {
    String t = tier == null || tier.isBlank() ? "TIER_1" : tier.trim().toUpperCase(Locale.ROOT);
    if (!ClientAccountStore.TIERS.contains(t)) throw new IllegalArgumentException("Invalid subscription tier");
    return t;
  }

  private static String normalizeNullable(String tier) {
    if (tier == null || tier.isBlank()) return "";
    try { return normalize(tier); } catch (Exception ex) { return ""; }
  }

  private static boolean isTerminalStatus(String status) {
    String s = clean(status).toLowerCase(Locale.ROOT);
    return s.isBlank() || List.of("canceled", "unpaid", "incomplete_expired", "paused").contains(s);
  }

  private static String metadata(Map<String,String> metadata, String key) {
    return metadata == null ? "" : clean(metadata.get(key));
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) if (value != null && !value.isBlank()) return value.trim();
    return "";
  }

  private static String safeMessage(Exception ex) {
    String m = ex.getMessage();
    return m == null || m.isBlank() ? ex.getClass().getSimpleName() : m;
  }

  private static String clean(String s) { return s == null ? "" : s.trim(); }

  private enum Access { GRANT, REVOKE, KEEP }
  private record BillingState(String customerId, String subscriptionId, String status, String tier) {}
}
