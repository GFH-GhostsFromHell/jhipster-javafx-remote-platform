package com.example.remoteportal.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class PortalHtmlController {
  @GetMapping(value = "/portal.html", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String portal(Authentication authentication) throws IOException {
    ClassPathResource resource = new ClassPathResource("static/portal.html");
    String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    boolean admin = authentication != null && authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    if (!admin) return html;
    return html.replace("</body>", "<script src=\"/admin-tier-inline.js\"></script></body>");
  }
}
