package com.example.remoteportal.web;

import com.example.remoteportal.service.PortalStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Controller
public class EngineeringPowerPageController {
  private final PortalStore store;

  public EngineeringPowerPageController(PortalStore store){this.store=store;}

  @GetMapping(value="/engineering-power.html",produces=MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public ResponseEntity<String> engineeringPower(Principal principal){
    boolean allowed=principal!=null && Boolean.TRUE.equals(store.effective(principal.getName()).get("ENGINEERING_POWER_SYSTEMS"));
    String body=allowed?page():lockedPage();
    return ResponseEntity.status(allowed?HttpStatus.OK:HttpStatus.FORBIDDEN)
        .contentType(MediaType.TEXT_HTML)
        .header(HttpHeaders.CACHE_CONTROL,"no-store, no-cache, must-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA,"no-cache")
        .body(body);
  }

  private static String page(){
    try {
      ClassPathResource resource=new ClassPathResource("engineering/engineering-power.html");
      return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return "<!doctype html><html><body><h1>Engineering Power Systems</h1><p>The engineering workspace could not be loaded.</p></body></html>";
    }
  }

  private static String lockedPage(){
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Engineering Power Systems</title><style>body{margin:0;background:#07111f;color:#e5eef8;font-family:Inter,Segoe UI,system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}.c{max-width:600px;margin:20px;padding:28px;border:1px solid #2b5068;border-radius:18px;background:#0b1f31}a{color:#67e8f9}</style></head><body><div class="c"><h1>Engineering Power Systems</h1><p>This workspace is included with Tier 2 and Tier 3. Your current account does not have the ENGINEERING_POWER_SYSTEMS feature enabled.</p><p><a href="/portal.html">Return to account portal</a></p></div></body></html>
""";
  }
}
