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
      String html=new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return html.replace("</body>", symmetricalReferencePatch()+"</body>");
    } catch (IOException ex) {
      return "<!doctype html><html><body><h1>Engineering Power Systems</h1><p>The engineering workspace could not be loaded.</p></body></html>";
    }
  }

  private static String symmetricalReferencePatch(){
    return """
<script>
(function(){
  const mode=document.getElementById('scMode');
  const button=document.getElementById('calcSc');
  if(!mode||!button||document.getElementById('scRef')) return;
  const field=document.createElement('div');
  field.className='field';
  field.innerHTML='<label>Sequence reference phase</label><select id="scRef"><option value="A">R / A</option><option value="B">W / B</option><option value="C">B / C</option></select><small>Used for sequence → power-system phases.</small>';
  mode.closest('.formgrid').appendChild(field);
  const ref=document.getElementById('scRef');
  const rad=d=>d*Math.PI/180, deg=r=>r*180/Math.PI;
  const C=(re,im)=>({re,im}), add=(a,b)=>C(a.re+b.re,a.im+b.im), mul=(a,b)=>C(a.re*b.re-a.im*b.im,a.re*b.im+a.im*b.re), scale=(a,k)=>C(a.re*k,a.im*k);
  const polar=(m,d)=>C(m*Math.cos(rad(d)),m*Math.sin(rad(d))), mag=a=>Math.hypot(a.re,a.im), ang=a=>deg(Math.atan2(a.im,a.re));
  const fmt=(v,d=4)=>Number.isFinite(v)?Number(v).toFixed(d).replace(/\\.?0+$/,''):'—';
  const ph=a=>fmt(mag(a),4)+' ∠ '+fmt(ang(a),2)+'°';
  function setLabels(){
    if(mode.value!=='seq') return;
    const r=ref.value;
    document.getElementById('scLabelA').textContent='Zero sequence (0) at '+r;
    document.getElementById('scLabelB').textContent='Positive sequence (+) at '+r;
    document.getElementById('scLabelC').textContent='Negative sequence (−) at '+r;
  }
  function recompute(){
    if(mode.value!=='seq') return;
    const z=polar(Number(document.getElementById('scAm').value),Number(document.getElementById('scAa').value));
    let p=polar(Number(document.getElementById('scBm').value),Number(document.getElementById('scBa').value));
    let neg=polar(Number(document.getElementById('scCm').value),Number(document.getElementById('scCa').value));
    const a=polar(1,120),a2=polar(1,240);
    if(ref.value==='B'){p=mul(a,p);neg=mul(a2,neg)}
    else if(ref.value==='C'){p=mul(a2,p);neg=mul(a,neg)}
    const pa=add(add(z,p),neg);
    const pb=add(add(z,mul(a2,p)),mul(a,neg));
    const pc=add(add(z,mul(a,p)),mul(a2,neg));
    document.getElementById('scOut0').textContent=ph(pa);
    document.getElementById('scOut1').textContent=ph(pb);
    document.getElementById('scOut2').textContent=ph(pc);
    document.getElementById('scNeutral').textContent=ph(scale(z,3));
    document.getElementById('scSteps').textContent='Sequence values are referenced to '+ref.value+'. Positive and negative sequence are first rotated to the R/A reference; zero sequence is not rotated. Then A = 0 + + + −; B = 0 + α²(+) + α(−); C = 0 + α(+) + α²(−).';
  }
  mode.addEventListener('change',setLabels);
  ref.addEventListener('change',setLabels);
  button.addEventListener('click',recompute);
  setLabels();
})();
</script>
""";
  }

  private static String lockedPage(){
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Engineering Power Systems</title><style>body{margin:0;background:#07111f;color:#e5eef8;font-family:Inter,Segoe UI,system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}.c{max-width:600px;margin:20px;padding:28px;border:1px solid #2b5068;border-radius:18px;background:#0b1f31}a{color:#67e8f9}</style></head><body><div class="c"><h1>Engineering Power Systems</h1><p>This workspace is included with Tier 2 and Tier 3. Your current account does not have the ENGINEERING_POWER_SYSTEMS feature enabled.</p><p><a href="/portal.html">Return to account portal</a></p></div></body></html>
""";
  }
}
