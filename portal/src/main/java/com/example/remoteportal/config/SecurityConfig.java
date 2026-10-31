package com.example.remoteportal.config;

import com.example.remoteportal.service.ClientAccountStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}

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
  SecurityFilterChain filter(HttpSecurity http) throws Exception {
    http.csrf(csrf->csrf.ignoringRequestMatchers("/api/**", "/login", "/logout"))
      .authorizeHttpRequests(a->a
        .requestMatchers("/", "/index.html", "/management/health", "/actuator/health", "/api/public/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/client/**", "/api/profile/**", "/portal.html").hasAnyRole("CLIENT", "ADMIN")
        .anyRequest().authenticated())
      .formLogin(f->f
        .loginPage("/")
        .loginProcessingUrl("/login")
        .defaultSuccessUrl("/portal.html", true)
        .failureUrl("/?error=1")
        .permitAll())
      .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/").permitAll())
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
