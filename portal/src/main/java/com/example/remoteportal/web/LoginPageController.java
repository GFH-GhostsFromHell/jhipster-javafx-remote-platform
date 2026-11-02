package com.example.remoteportal.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginPageController {
  private static final String ACCOUNT_LINKS = """
    <div style="margin-top:16px;text-align:center;font-size:13px;line-height:1.8">
      <a href="/account.html#signup" style="color:#0e7490;font-weight:800;text-decoration:none">Create account</a>
      <span style="color:#cbd5e1;margin:0 7px">•</span>
      <a href="/account.html#forgot-password" style="color:#475569;text-decoration:none">Forgot password?</a>
      <span style="color:#cbd5e1;margin:0 7px">•</span>
      <a href="/account.html#forgot-username" style="color:#475569;text-decoration:none">Forgot username?</a>
    </div>
    """;

  private final Resource index;

  public LoginPageController(ResourceLoader resources) {
    this.index = resources.getResource("classpath:/static/index.html");
  }

  @GetMapping(value={"/","/index.html"}, produces=MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String login() throws IOException {
    String html = index.getContentAsString(StandardCharsets.UTF_8);
    if (!html.contains("/account.html#signup")) {
      html = html.replace("</form>", "</form>\n" + ACCOUNT_LINKS);
    }
    return html;
  }
}
