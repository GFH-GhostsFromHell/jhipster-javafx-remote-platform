package com.example.remoteportal.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the existing portal page with additive navigation and management scripts.
 */
@Controller
public class PortalPageController {
  private static final String CALCULATOR_LINK="<a href=\"/calculators.html\"><span class=\"icon\">∑</span><span class=\"label\">CEC Calculators</span></a>";
  private static final String CHATGPT_LINK="<a href=\"/chatgpt.html\"><span class=\"icon\">✦</span><span class=\"label\">Ask ChatGPT</span></a>";
  private static final String MANAGEMENT_SCRIPTS="""
<script src="/admin-tier-inline.js"></script>
<script src="/enterprise-team.js"></script>
<script src="/enterprise-deeplink.js"></script>
""";
  private static final String DEEPLINK_SCRIPT="<script src=\"/enterprise-deeplink.js\"></script>";
  private final Resource portal;

  public PortalPageController(ResourceLoader resources){this.portal=resources.getResource("classpath:/static/portal.html");}

  @GetMapping(value="/portal.html",produces=MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String portal() throws IOException {
    String html=portal.getContentAsString(StandardCharsets.UTF_8);
    if(!html.contains(CHATGPT_LINK)) html=html.replace(CALCULATOR_LINK,CALCULATOR_LINK+"\n      "+CHATGPT_LINK);
    if(!html.contains("/enterprise-team.js")) html=html.replace("</body>",MANAGEMENT_SCRIPTS+"\n</body>");
    else if(!html.contains("/enterprise-deeplink.js")) html=html.replace("</body>",DEEPLINK_SCRIPT+"\n</body>");
    return html;
  }
}
