package com.example.remoteportal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
  @Bean UserDetailsService users(@Value("${app.admin.username:admin}") String u,@Value("${app.admin.password:change-me-now}") String p, PasswordEncoder e){return new InMemoryUserDetailsManager(User.withUsername(u).password(e.encode(p)).roles("ADMIN").build());}
  @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
    http.csrf(csrf->csrf.ignoringRequestMatchers("/api/**"))
      .authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/management/health","/api/public/**").permitAll().anyRequest().authenticated())
      .formLogin(Customizer.withDefaults()).httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
