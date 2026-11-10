package com.gfh.industrial;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class StudioSecurity {
    @Bean
    UserDetailsService studioUsers(
        @Value("${studio.admin-user}") String admin,
        @Value("${studio.admin-password}") String adminPassword,
        @Value("${studio.viewer-user}") String viewer,
        @Value("${studio.viewer-password}") String viewerPassword) {
        if (adminPassword.length() < 12 || viewerPassword.length() < 12 || admin.equals(viewer) || adminPassword.equals(viewerPassword)) {
            throw new IllegalStateException("Set distinct STUDIO_ADMIN_PASSWORD and STUDIO_VIEWER_PASSWORD (12+ characters each)");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
            User.withUsername(admin).password("{bcrypt}"+encoder.encode(adminPassword)).roles("ADMIN", "VIEWER").build(),
            User.withUsername(viewer).password("{bcrypt}"+encoder.encode(viewerPassword)).roles("VIEWER").build()
        );
    }

    @Bean
    SecurityFilterChain studioFilter(HttpSecurity http) throws Exception {
        // Loopback-only prototype, Basic credentials supplied explicitly by client.
        // Must be replaced by session+CSRF / mTLS architecture before production.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/studio/config").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/studio/config").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/screen-designer.js",
                    "/showcase.html", "/showcase.js", "/api/studio/showcase").permitAll()
                // The administrative preview exposes all sample tags. Workstation endpoints are separately scoped.
                // Pairing code is 256-bit, short-lived, one-use and is validated in the endpoint.
                .requestMatchers(HttpMethod.POST, "/api/studio/pair").permitAll()
                // Workstation layout/data endpoints authorize the registered device secret
                // in ScreenApi; shared viewer credentials are no longer required.
                .requestMatchers(HttpMethod.GET, "/api/studio/client-screen", "/api/studio/client-live",
                    "/api/studio/client-update-status", "/api/studio/client-app-manifest").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/studio/client-update-ack").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/studio/live").hasRole("ADMIN")
                .requestMatchers("/api/studio/screens", "/api/studio/screens/**", "/api/studio/workstations", "/api/studio/workstations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/studio/status", "/api/studio/update-manifest").hasAnyRole("ADMIN", "VIEWER")
                .anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
