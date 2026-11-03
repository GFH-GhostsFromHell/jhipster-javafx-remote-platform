package com.example.remoteportal.config;

import com.example.remoteportal.service.EnterpriseAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

@Component
public class EnterpriseBillingGuardFilter extends OncePerRequestFilter {
  private final EnterpriseAccountService enterprise;

  public EnterpriseBillingGuardFilter(EnterpriseAccountService enterprise) {
    this.enterprise = enterprise;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/api/profile/checkout/")) {
      Principal principal = request.getUserPrincipal();
      if (principal != null && enterprise.isManagedMember(principal.getName())) {
        String owner = enterprise.managingOwner(principal.getName());
        response.sendError(HttpServletResponse.SC_CONFLICT,
            "Your subscription tier is managed by your Business / Enterprise account" + (owner.isBlank() ? "." : " (" + owner + ")."));
        return;
      }
    }
    filterChain.doFilter(request, response);
  }
}
