package com.example.remoteportal.web;

import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.PortalStore;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
public class PortalResource {
  private final PortalStore store;
  private final ClientAccountStore accounts;

  public PortalResource(PortalStore store, ClientAccountStore accounts){
    this.store=store;
    this.accounts=accounts;
  }

  @GetMapping("/api/public/client-health")
  public Map<String,Object> publicHealth(){
    return Map.of("status","UP","serverTime",Instant.now().toString(),"apiVersion","0.4.0");
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
    Map<String,Object> account;
    try { account=accounts.get(u); }
    catch(Exception ex) { account=Map.of("username",u,"displayName",u,"enabled",true,"subscriptionTier","ADMIN"); }
    return Map.of(
      "user",account,
      "features",store.effective(u),
      "configuration",store.config(u),
      "serverTime",Instant.now().toString()
    );
  }

  @GetMapping("/api/profile")
  public Map<String,Object> profile(Principal p){
    try { return accounts.get(p.getName()); }
    catch(Exception ex) {
      return Map.of("username",p.getName(),"displayName",p.getName(),"email","","enabled",true,"subscriptionTier","ADMIN");
    }
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
