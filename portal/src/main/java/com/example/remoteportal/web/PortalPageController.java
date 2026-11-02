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
 * Serves the existing portal page unchanged except for one additive Ask ChatGPT
 * navigation link. Keeping this as a tiny server-side enhancement avoids
 * duplicating or replacing the established portal UI.
 */
@Controller
public class PortalPageController {
  private static final String CALCULATOR_LINK="<a href=\"/calculators.html\"><span class=\"icon\">∑</span><span class=\"label\">CEC Calculators</span></a>";
  private static final String CHATGPT_LINK="<a href=\"/chatgpt.html\"><span class=\"icon\">✦</span><span class=\"label\">Ask ChatGPT</span></a>";
  private final Resource portal;

  public PortalPageController(ResourceLoader resources){this.portal=resources.getResource("classpath:/static/portal.html");}

  @GetMapping(value="/portal.html",produces=MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String portal() throws IOException {
    String html=portal.getContentAsString(StandardCharsets.UTF_8);
    if(html.contains(CHATGPT_LINK))return html;
    return html.replace(CALCULATOR_LINK,CALCULATOR_LINK+"\n      "+CHATGPT_LINK);
  }
}
