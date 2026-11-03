package com.example.remoteportal.web;

import com.example.remoteportal.service.EnterpriseAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprise")
public class EnterpriseResource {
  private final EnterpriseAccountService enterprise;

  public EnterpriseResource(EnterpriseAccountService enterprise) {
    this.enterprise = enterprise;
  }

  @GetMapping
  public Map<String,Object> dashboard(Principal principal) {
    return ownerCall(() -> enterprise.dashboard(principal.getName()));
  }

  @GetMapping("/membership")
  public Map<String,Object> membership(Principal principal) {
    return enterprise.membership(principal.getName());
  }

  public record BusinessUpdate(String businessName) {}

  @PutMapping
  public Map<String,Object> renameBusiness(Principal principal, @RequestBody BusinessUpdate update) {
    return ownerCall(() -> enterprise.renameBusiness(principal.getName(), update == null ? null : update.businessName()));
  }

  public record MemberAdd(String username, String subscriptionTier, String roleLabel) {}

  @PostMapping("/members")
  public Map<String,Object> addMember(Principal principal, @RequestBody MemberAdd update) {
    return ownerCall(() -> enterprise.addMember(
        principal.getName(),
        update == null ? null : update.username(),
        update == null ? null : update.subscriptionTier(),
        update == null ? null : update.roleLabel()));
  }

  public record TierUpdate(String subscriptionTier) {}

  @PutMapping("/members/{username}/tier")
  public Map<String,Object> updateTier(Principal principal, @PathVariable String username, @RequestBody TierUpdate update) {
    return ownerCall(() -> enterprise.updateMemberTier(principal.getName(), username, update == null ? null : update.subscriptionTier()));
  }

  public record RoleUpdate(String roleLabel) {}

  @PutMapping("/members/{username}/role")
  public Map<String,Object> updateRole(Principal principal, @PathVariable String username, @RequestBody RoleUpdate update) {
    return ownerCall(() -> enterprise.updateMemberRole(principal.getName(), username, update == null ? null : update.roleLabel()));
  }

  @DeleteMapping("/members/{username}")
  public Map<String,Object> removeMember(Principal principal, @PathVariable String username) {
    return ownerCall(() -> enterprise.removeMember(principal.getName(), username));
  }

  private Map<String,Object> ownerCall(Action action) {
    try {
      return action.run();
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @FunctionalInterface
  private interface Action { Map<String,Object> run(); }
}
