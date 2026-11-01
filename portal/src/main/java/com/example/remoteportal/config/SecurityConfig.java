package com.example.remoteportal.config;

import com.example.remoteportal.service.ClientAccountStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.core.user.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}

  @Bean
  ClientRegistrationRepository clientRegistrationRepository(
      @Value("${GOOGLE_CLIENT_ID:}") String clientId,
      @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      return registrationId -> null;
    }
    ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
        .clientId(clientId.trim())
        .clientSecret(clientSecret.trim())
        .scope("openid", "profile", "email")
        .build();
    return new InMemoryClientRegistrationRepository(google);
  }

  @Bean
  OAuth2UserService<OidcUserRequest, OidcUser> googleOidcUserService(ClientAccountStore accounts) {
    OidcUserService delegate = new OidcUserService();
    return request -> {
      OidcUser google = delegate.loadUser(request);
      String email = google.getEmail();
      if (email == null || email.isBlank()) throw new IllegalArgumentException("Google account did not provide an email address");
      accounts.ensureGoogleAccount(email, google.getFullName(), google.getSubject());
      Set<GrantedAuthority> authorities = new LinkedHashSet<>(google.getAuthorities());
      authorities.add(new SimpleGrantedAuthority("ROLE_CLIENT"));
      if (google.getUserInfo() != null) return new DefaultOidcUser(authorities, google.getIdToken(), google.getUserInfo(), "email");
      return new DefaultOidcUser(authorities, google.getIdToken(), "email");
    };
  }

  @Bean
  UserDetailsService users(
      @Value("${app.admin.username:admin}") String adminUser,
      @Value("${app.admin.password:change-me-now}") String adminPassword,
      PasswordEncoder encoder,
      ClientAccountStore clientAccounts) {
    UserDetails admin = User.withUsername(adminUser)
        .password(encoder.encode(adminPassword))
        .roles("ADMIN")
        .build();
    return username -> {
      if (adminUser.equalsIgnoreCase(username)) return admin;
      return clientAccounts.loadUserByUsername(username);
    };
  }

  @Bean
  SecurityFilterChain filter(HttpSecurity http, OAuth2UserService<OidcUserRequest, OidcUser> googleOidcUserService) throws Exception {
    http.csrf(csrf->csrf.ignoringRequestMatchers("/api/**", "/login", "/logout"))
      .authorizeHttpRequests(a->a
        .requestMatchers("/", "/index.html", "/management/health", "/actuator/health", "/api/public/**", "/oauth2/**", "/login/oauth2/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/client/**", "/api/profile/**", "/portal.html").hasAnyRole("CLIENT", "ADMIN")
        .anyRequest().authenticated())
      .formLogin(f->f
        .loginPage("/")
        .loginProcessingUrl("/login")
        .defaultSuccessUrl("/portal.html", true)
        .failureUrl("/?error=1")
        .permitAll())
      .oauth2Login(o->o
        .loginPage("/")
        .userInfoEndpoint(u->u.oidcUserService(googleOidcUserService))
        .defaultSuccessUrl("/portal.html", true)
        .failureUrl("/?googleError=1"))
      .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/").permitAll())
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
