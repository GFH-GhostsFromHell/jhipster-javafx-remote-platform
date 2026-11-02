package com.example.remoteportal.web;

import com.example.remoteportal.service.AccountLifecycleService;
import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.TierCheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

@RestController
public class PublicAccountResource {
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
    return Map.of("emailConfigured", accounts.mailConfigured(), "tiers", checkout.options());
  }

  public record RegisterStart(String username, String displayName, String email, String password, String requestedTier) {}

  @PostMapping("/api/public/register/start")
  public Map<String,Object> registerStart(@RequestBody RegisterStart request) {
    requireMail();
    try {
      return accounts.beginRegistration(request.username(), request.displayName(), request.email(), request.password(), request.requestedTier());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The verification email could not be sent. Please try again.");
    }
  }

  public record RegisterVerify(String username, String code) {}

  @PostMapping("/api/public/register/verify")
  public Map<String,Object> registerVerify(@RequestBody RegisterVerify request) {
    try {
      Map<String,Object> verified = new LinkedHashMap<>(accounts.verifyRegistration(request.username(), request.code()));
      String requestedTier = Objects.toString(verified.get("requestedTier"), "TIER_1");
      checkout.checkoutUrl(requestedTier).ifPresent(url -> verified.put("checkoutUrl", url));
      verified.put("checkoutConfigured", checkout.configured(requestedTier));
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
  public Map<String,Object> publicCheckout(@PathVariable String tier) {
    try {
      String normalized = TierCheckoutService.normalize(tier);
      Map<String,Object> result = new LinkedHashMap<>();
      result.put("tier", normalized);
      Optional<String> url = checkout.checkoutUrl(normalized);
      result.put("configured", url.isPresent() || "TIER_1".equals(normalized));
      url.ifPresent(v -> result.put("url", v));
      return result;
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/api/profile/billing-options")
  public Map<String,Object> billingOptions(Principal principal) {
    String tier;
    try { tier = clientAccounts.tier(principal.getName()); }
    catch (Exception ex) { tier = "ADMIN"; }
    return Map.of("currentTier", tier, "tiers", checkout.options());
  }

  private void requireMail() {
    if (!accounts.mailConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Email verification and recovery are not configured yet. Configure SMTP for this portal first.");
  }
}
