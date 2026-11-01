package com.example.remoteportal.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class CalculatorWorkspacePageController {

  @GetMapping(value = "/calculators.html", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String calculatorWorkspace() throws IOException {
    ClassPathResource resource = new ClassPathResource("static/calculators.html");
    String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    html = html.replace(
        "Account portal</a>",
        "Account portal</a> <a href=\"/bend-calcs.html\">Visual Bend Calcs</a>");

    String enhancement = """
      <style>
        .visual-bend-entry{display:block;margin-top:12px;padding:13px 14px;border-radius:11px;background:linear-gradient(135deg,#0f4c81,#0e7490);color:#fff;text-decoration:none;font-weight:850;box-shadow:0 8px 20px #0f172a18}
        .visual-bend-entry small{display:block;margin-top:4px;color:#d7f5ff;font-weight:500;line-height:1.35}
      </style>
      <script>
      (function(){
        function addVisualBendEntry(){
          document.querySelectorAll('.card h3').forEach(function(h){
            if(h.textContent.trim()==='Conduit bending' && !h.parentElement.querySelector('.visual-bend-entry')){
              var a=document.createElement('a');
              a.href='/bend-calcs.html';
              a.className='visual-bend-entry';
              a.innerHTML='Open Visual Bend Calcs →<small>Diagram-led offsets, rolling offsets, saddles, stub-ups, kicked/segmented 90s, parallel bends and obstruction layouts.</small>';
              h.parentElement.appendChild(a);
            }
          });
        }
        if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',function(){setTimeout(addVisualBendEntry,0)});
        else setTimeout(addVisualBendEntry,0);
        setTimeout(addVisualBendEntry,250);
      })();
      </script>
      """;

    return html.replace("</body>", enhancement + "</body>");
  }
}
