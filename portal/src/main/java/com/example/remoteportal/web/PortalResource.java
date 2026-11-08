package com.example.remoteportal.web;

import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.PortalStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
public class PortalResource {
  private final PortalStore store;
  private final ClientAccountStore accounts;
  private final boolean googleEnabled;

  public PortalResource(PortalStore store, ClientAccountStore accounts,
      @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
      @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret){
    this.store=store;
    this.accounts=accounts;
    this.googleEnabled=googleClientId!=null&&!googleClientId.isBlank()&&googleClientSecret!=null&&!googleClientSecret.isBlank();
  }

  @GetMapping("/api/public/client-health")
  public Map<String,Object> publicHealth(){
    return Map.of("status","UP","serverTime",Instant.now().toString(),"apiVersion","0.5.0");
  }

  @GetMapping("/api/public/auth-options")
  public Map<String,Object> authOptions(){
    return Map.of("password",true,"google",googleEnabled);
  }

  @GetMapping("/api/client/status")
  public Map<String,Object> status(Principal p){
    return Map.of(
      "cloud","CONNECTED",
      "authenticated",true,
      "configurationCurrent",true,
      "username",p.getName(),
      "serverTime",Instant.now().toString()
    );
  }

  @PostMapping("/api/client/login")
  public Map<String,Object> login(Principal p){ return bootstrap(p); }

  @GetMapping("/api/client/bootstrap")
  public Map<String,Object> bootstrap(Principal p){
    String u=p.getName();
    Map<String,Object> account=accountForPrincipal(p);
    return Map.of(
      "user",account,
      "features",store.effective(u),
      "configuration",store.config(u),
      "serverTime",Instant.now().toString()
    );
  }

  @GetMapping("/api/profile")
  public Map<String,Object> profile(Principal p){
    return accountForPrincipal(p);
  }

  private Map<String,Object> accountForPrincipal(Principal principal) {
    try {
      return accounts.get(principal.getName());
    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException missing) {
      // The in-memory administrator is not a client_account database row.
      // Only authenticated principals with the actual admin authority qualify.
      if (isAdmin(principal)) return Map.of(
          "username",principal.getName(),"displayName",principal.getName(),
          "email","","enabled",true,"subscriptionTier","ADMIN");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Your account profile could not be loaded. Please try again.");
    } catch (Exception error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Your account profile is temporarily unavailable. Please try again.");
    }
  }

  private static boolean isAdmin(Principal principal) {
    return principal instanceof Authentication auth && auth.isAuthenticated()
        && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
  }

  public record ProfileUpdate(String displayName,String email,String avatarData){}

  @PutMapping("/api/profile")
  public Map<String,Object> updateProfile(Principal p,@RequestBody ProfileUpdate update){
    accounts.updateProfile(p.getName(), update.displayName(), update.email(), update.avatarData());
    return accounts.get(p.getName());
  }

  public record PasswordChange(String currentPassword,String newPassword){}

  @PutMapping("/api/profile/password")
  public Map<String,Object> changePassword(Principal p,@RequestBody PasswordChange update){
    accounts.changePassword(p.getName(), update.currentPassword(), update.newPassword());
    return Map.of("changed",true);
  }

  @GetMapping("/api/admin/features")
  public List<String> features(){return PortalStore.FEATURES;}

  @GetMapping("/api/admin/tiers")
  public List<String> tiers(){return ClientAccountStore.TIERS;}

  @GetMapping("/api/admin/users")
  public List<Map<String,Object>> users(){return accounts.list();}

  public record UserUpdate(String displayName,String email,String password,Boolean enabled,String subscriptionTier){}

  @PutMapping("/api/admin/users/{username}")
  public Map<String,Object> upsertUser(@PathVariable String username,@RequestBody UserUpdate update){
    boolean enabled=update.enabled()==null || update.enabled();
    accounts.upsert(username,update.displayName(),update.email(),update.password(),enabled,update.subscriptionTier());
    return accounts.get(username);
  }

  @GetMapping("/api/admin/users/{username}/features")
  public Map<String,Object> userFeatures(@PathVariable String username){
    return Map.of("username",username,"subscriptionTier",accounts.tier(username),"overrides",store.overrides(username),"effective",store.effective(username));
  }

  public record FeatureUpdate(String featureKey,String state){}

  @PutMapping("/api/admin/users/{username}/features")
  public Map<String,Object> setFeature(@PathVariable String username,@RequestBody FeatureUpdate update,Authentication auth){
    store.setFeature(username,update.featureKey(),update.state(),auth.getName());
    return userFeatures(username);
  }

  @GetMapping("/api/admin/users/{username}/configuration")
  public Map<String,String> config(@PathVariable String username){return store.config(username);}

  public record ConfigUpdate(String key,String value){}

  @PutMapping("/api/admin/users/{username}/configuration")
  public Map<String,String> setConfig(@PathVariable String username,@RequestBody ConfigUpdate update){
    store.setConfig(username,update.key(),update.value());
    return store.config(username);
  }
}
