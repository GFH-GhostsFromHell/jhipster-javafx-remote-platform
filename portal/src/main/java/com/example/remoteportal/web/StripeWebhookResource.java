package com.example.remoteportal.web;

import com.example.remoteportal.service.TierCheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class StripeWebhookResource {
  private final TierCheckoutService billing;

  public StripeWebhookResource(TierCheckoutService billing) {
    this.billing = billing;
  }

  @PostMapping(value="/api/public/stripe/webhook", consumes=MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> webhook(@RequestBody String payload,
                                    @RequestHeader(value="Stripe-Signature", required=false) String signature) {
    if (signature == null || signature.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe-Signature header");
    }
    try {
      billing.handleWebhook(payload, signature);
      return Map.of("received", true);
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe webhook verification or processing failed");
    }
  }
}
