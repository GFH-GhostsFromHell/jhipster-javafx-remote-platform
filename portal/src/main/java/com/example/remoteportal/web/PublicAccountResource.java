package com.example.remoteportal.web;

import com.example.remoteportal.service.AccountLifecycleService;
import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.TierCheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

@RestController
public class PublicAccountResource {
  private static final Logger LOG = LoggerFactory.getLogger(PublicAccountResource.class);
  private final AccountLifecycleService accounts;
  private final ClientAccountStore clientAccounts;
  private final TierCheckoutService checkout;

  public PublicAccountResource(AccountLifecycleService accounts, ClientAccountStore clientAccounts, TierCheckoutService checkout) {
    this.accounts = accounts;
    this.clientAccounts = clientAccounts;
    this.checkout = checkout;
  }

  @GetMapping("/api/public/account-options")
  public Map<String,Object> accountOptions() {
    return Map.of(
        "emailConfigured", accounts.mailConfigured(),
        "stripeConfigured", checkout.webhookConfigured(),
        "tiers", checkout.options());
  }

  public record RegisterStart(String username, String displayName, String email, String password, String requestedTier) {}

  @PostMapping("/api/public/register/start")
  public Map<String,Object> registerStart(@RequestBody RegisterStart request) {
    requireMail();
    try {
      return accounts.beginRegistration(request.username(), request.displayName(), request.email(), request.password(), request.requestedTier());
    } catch (IllegalArgumentException ex) {
      LOG.info("Registration rejected: {}", ex.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (MailException ex) {
      LOG.warn("Registration SMTP delivery failure (type={} cause={})", ex.getClass().getSimpleName(), causeType(ex));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The verification email could not be sent by Mailjet. Check Mailjet activity and sender settings.");
    } catch (DataAccessException ex) {
      LOG.error("Registration database failure (type={} cause={})", ex.getClass().getSimpleName(), causeType(ex));
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Registration could not be saved in the database. Please contact support.");
    } catch (RuntimeException ex) {
      LOG.error("Registration unexpected server failure (type={} cause={})", ex.getClass().getSimpleName(), causeType(ex));
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration could not be completed. Please contact support.");
    }
  }

  public record RegisterVerify(String username, String code) {}

  @PostMapping("/api/public/register/verify")
  public Map<String,Object> registerVerify(@RequestBody RegisterVerify request) {
    try {
      Map<String,Object> verified = new LinkedHashMap<>(accounts.verifyRegistration(request.username(), request.code()));
      String requestedTier = Objects.toString(verified.get("requestedTier"), "TIER_1");
      verified.put("checkoutConfigured", checkout.configured(requestedTier));
      if (!"TIER_1".equals(requestedTier) && checkout.configured(requestedTier)) {
        try {
          verified.put("checkoutUrl", checkout.createCheckoutSession(request.username(), requestedTier));
        } catch (RuntimeException ex) {
          verified.put("checkoutError", ex.getMessage());
        }
      }
      return verified;
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  public record EmailRequest(String email) {}

  @PostMapping("/api/public/recovery/username")
  public Map<String,Object> forgotUsername(@RequestBody EmailRequest request) {
    requireMail();
    try {
      accounts.sendUsernameReminder(request.email());
      return Map.of("sent", true, "message", "If that email is associated with an account, a username reminder has been sent.");
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (RuntimeException ex) {
      LOG.warn("Username reminder email could not be delivered (type={} cause={})", ex.getClass().getSimpleName(), causeType(ex));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The reminder email could not be sent. Please try again.");
    }
  }

  public record PasswordResetStart(String account) {}

  @PostMapping("/api/public/recovery/password/start")
  public Map<String,Object> passwordResetStart(@RequestBody PasswordResetStart request) {
    requireMail();
    try {
      accounts.beginPasswordReset(request.account());
      return Map.of("sent", true, "message", "If a password account matches, a reset code has been sent to its email address.");
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (RuntimeException ex) {
      LOG.warn("Password reset email could not be delivered (type={} cause={})", ex.getClass().getSimpleName(), causeType(ex));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The reset email could not be sent. Please try again.");
    }
  }

  public record PasswordResetComplete(String username, String code, String newPassword) {}

  @PostMapping("/api/public/recovery/password/complete")
  public Map<String,Object> passwordResetComplete(@RequestBody PasswordResetComplete request) {
    try {
      accounts.completePasswordReset(request.username(), request.code(), request.newPassword());
      return Map.of("changed", true, "message", "Your password has been reset. You can sign in now.");
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/api/public/checkout/{tier}")
  public Map<String,Object> publicCheckoutStatus(@PathVariable String tier) {
    try {
      String normalized = TierCheckoutService.normalize(tier);
      return Map.of(
          "tier", normalized,
          "configured", checkout.configured(normalized),
          "provider", "stripe",
          "requiresVerifiedAccount", !"TIER_1".equals(normalized));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/api/profile/billing-options")
  public Map<String,Object> billingOptions(Principal principal) {
    try { return checkout.profileOptions(principal.getName()); }
    catch (Exception ex) {
      return Map.of("currentTier", safeTier(principal), "tiers", checkout.options(), "stripeConfigured", false);
    }
  }

  @PostMapping("/api/profile/checkout/{tier}")
  public Map<String,Object> profileCheckout(Principal principal, @PathVariable String tier) {
    try {
      String normalized = TierCheckoutService.normalize(tier);
      if ("TIER_1".equals(normalized)) throw new IllegalArgumentException("Tier 1 does not require checkout.");
      String url = checkout.createCheckoutSession(principal.getName(), normalized);
      return Map.of("tier", normalized, "url", url);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
  }

  @PostMapping("/api/profile/billing-portal")
  public Map<String,Object> billingPortal(Principal principal) {
    try {
      return Map.of("url", checkout.createBillingPortalSession(principal.getName()));
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
  }

  private String safeTier(Principal principal) {
    try { return clientAccounts.tier(principal.getName()); }
    catch (Exception ex) { return "ADMIN"; }
  }

  private static String causeType(Throwable ex) {
    Throwable cause = ex;
    for (int i = 0; i < 5 && cause.getCause() != null; i++) cause = cause.getCause();
    return cause.getClass().getSimpleName();
  }

  /**
   * Consistent, safe JSON errors for account forms. Spring's default error body
   * can omit ResponseStatusException.reason, leaving the browser with only
   * a generic failure even when a specific validation error is known.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String,Object>> accountError(ResponseStatusException ex) {
    String reason = Objects.toString(ex.getReason(), "The request could not be completed.");
    String code = "REQUEST_FAILED";
    String field = "";
    if (reason.contains("username is already in use")) { code = "USERNAME_IN_USE"; field = "username"; }
    else if (reason.contains("email address is already associated")) { code = "EMAIL_IN_USE"; field = "email"; }
    else if (reason.startsWith("Username must")) { code = "INVALID_USERNAME"; field = "username"; }
    else if (reason.startsWith("Enter a valid email")) { code = "INVALID_EMAIL"; field = "email"; }
    else if (reason.contains("Passwords do not match")) { code = "PASSWORD_MISMATCH"; field = "password"; }
    else if (reason.contains("at least 8 characters")) { code = "INVALID_PASSWORD"; field = "password"; }
    else if (reason.contains("verification code is invalid or expired") || reason.contains("6-digit code")) { code = "INVALID_CODE"; field = "code"; }
    else if (reason.contains("verification email could not be sent")) { code = "EMAIL_DELIVERY_FAILED"; }
    else if (reason.contains("Registration could not be saved")) { code = "REGISTRATION_DATABASE_ERROR"; }
    else if (reason.contains("Email verification and recovery are not configured")) { code = "EMAIL_NOT_CONFIGURED"; }
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
        "message", reason, "code", code, "field", field, "status", ex.getStatusCode().value()));
  }

  private void requireMail() {
    if (!accounts.mailConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Email verification and recovery are not configured yet. Configure SMTP for this portal first.");
  }
}
