package com.example.remoteportal.web;

import com.example.remoteportal.service.PortalStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Engineering Power Systems · Electrical Workbench</title>
<style>
:root{font-family:Inter,Segoe UI,system-ui,sans-serif;color:#e5eef8;background:#07111f}*{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#07111f,#0b2743 48%,#10263a);min-height:100vh}.top{position:sticky;top:0;z-index:5;display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 20px;background:#07111ff2;border-bottom:1px solid #25506d}.top strong{font-size:18px}.top a{color:#dff8ff;text-decoration:none;border:1px solid #2d607e;border-radius:9px;padding:8px 11px;margin-left:6px}.wrap{max-width:1250px;margin:auto;padding:24px}.hero{padding:26px;border:1px solid #2f607c;border-radius:18px;background:linear-gradient(135deg,#10263a,#0e4b68);box-shadow:0 18px 45px #0005}.eyebrow{font-size:11px;font-weight:800;letter-spacing:.12em;color:#67e8f9}.hero h1{margin:7px 0 8px;font-size:30px}.hero p{margin:0;max-width:900px;color:#c6d4e2;line-height:1.55}.tier{display:inline-block;margin-top:14px;padding:6px 10px;border-radius:999px;background:#0e7490;color:white;font-size:12px;font-weight:800}.tabs{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0}.tabs button{border:1px solid #32627e;background:#10263a;color:#dbeafe;border-radius:999px;padding:9px 13px;font-weight:800;cursor:pointer}.tabs button.active{background:#0e7490;border-color:#22d3ee;color:white}.pane{display:none}.pane.active{display:block}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.card{background:#0b1f31;border:1px solid #2b5068;border-radius:16px;padding:18px;box-shadow:0 8px 24px #0002}.card h2,.card h3{margin-top:0}.card p,.card li{color:#b7c8d8;line-height:1.5}.status{font-size:11px;font-weight:850;color:#67e8f9;text-transform:uppercase;letter-spacing:.08em}.calc{padding:13px;border:1px dashed #3d6c87;border-radius:12px;background:#0b2235;margin-top:10px}.calc b{display:block;color:#e7f7ff;margin-bottom:4px}.calc span{color:#9eb3c5;font-size:13px}.note{margin-top:18px;padding:16px;border-radius:13px;background:#142d40;border:1px solid #35627c;color:#d6e5f0}.note strong{color:#67e8f9}@media(max-width:760px){.grid{grid-template-columns:1fr}.wrap{padding:14px}.hero h1{font-size:25px}.top{align-items:flex-start;flex-direction:column}.top div:last-child{display:flex;flex-wrap:wrap}.top a{margin:0 6px 0 0}}
</style>
</head>
<body>
<header class="top"><strong>⚡ Engineering Power Systems</strong><div><a href="/calculators.html">CEC Workbench</a><a href="/portal.html">Portal</a></div></header>
<main class="wrap">
<section class="hero"><div class="eyebrow">ENGINEERING · POWER SYSTEMS</div><h1>Transmission, Distribution, Protection & Controls</h1><p>This workspace is intentionally being built around your engineering notes. The sections and calculation slots are ready, but formulas, constants, assumptions and reference methods will not be hard-coded until you provide the notes you want used.</p><span class="tier">Included with Tier 2 and Tier 3</span></section>
<div class="tabs"><button class="active" onclick="show(0,this)">Transmission & Distribution</button><button onclick="show(1,this)">Protection & Controls</button><button onclick="show(2,this)">Calculation Library</button></div>
<section class="pane active">
  <div class="grid">
    <article class="card"><div class="status">Workspace ready</div><h2>Transmission</h2><p>Reserved for line and bulk-power calculations.</p>
      <div class="calc"><b>3-phase line loading / power flow</b><span>Awaiting your notes and preferred method.</span></div>
      <div class="calc"><b>Line impedance, losses & efficiency</b><span>Awaiting your notes and conductor/model assumptions.</span></div>
      <div class="calc"><b>Voltage regulation / receiving-end voltage</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Per-unit system</b><span>Awaiting base-value and conversion notes.</span></div>
    </article>
    <article class="card"><div class="status">Workspace ready</div><h2>Distribution</h2><p>Reserved for substations, feeders and distribution-system studies.</p>
      <div class="calc"><b>Feeder loading & voltage profile</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Transformer / substation loading</b><span>Awaiting your engineering calculation workflow.</span></div>
      <div class="calc"><b>Fault level / short-circuit study</b><span>Awaiting your preferred method and assumptions.</span></div>
      <div class="calc"><b>Demand, diversity & load allocation</b><span>Awaiting your notes.</span></div>
    </article>
  </div>
</section>
<section class="pane">
  <div class="grid">
    <article class="card"><div class="status">Workspace ready</div><h2>Protection</h2><p>Reserved for relay and protection-system calculations.</p>
      <div class="calc"><b>CT / PT ratio and secondary quantities</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Relay pickup / setting calculations</b><span>Awaiting your relay-setting method.</span></div>
      <div class="calc"><b>Time-overcurrent & coordination</b><span>Awaiting curve and grading-margin notes.</span></div>
      <div class="calc"><b>Differential / transformer protection</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Distance / line-protection zones</b><span>Awaiting your preferred zone-setting method.</span></div>
    </article>
    <article class="card"><div class="status">Workspace ready</div><h2>Controls</h2><p>Reserved for substation control and protection-control logic.</p>
      <div class="calc"><b>Breaker trip / close DC circuits</b><span>Awaiting control-power and coil notes.</span></div>
      <div class="calc"><b>Relay I/O, seal-in & interlocking</b><span>Awaiting your control-logic notes.</span></div>
      <div class="calc"><b>Protection zones & breaker mapping</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Trip matrix / cause-and-effect</b><span>Awaiting your notes and preferred layout.</span></div>
    </article>
  </div>
</section>
<section class="pane">
  <div class="grid">
    <article class="card"><div class="status">Notes-driven</div><h2>Calculation library</h2><p>Each future calculator can carry its own inputs, units, formula steps, assumptions, result, worked example and engineering reference.</p><ul><li>Formula / equation</li><li>Required inputs and accepted units</li><li>Constants and default assumptions</li><li>Step-by-step solution</li><li>Validation limits / warnings</li><li>Reference source or training note</li></ul></article>
    <article class="card"><div class="status">No invented formulas</div><h2>How your notes will be added</h2><p>When you upload notes, worksheets, screenshots or examples, the calculation can be implemented into the matching slot and tested against the worked examples before being enabled.</p></article>
  </div>
</section>
<div class="note"><strong>Current status:</strong> navigation and engineering categories are active. Calculation engines are intentionally placeholders until your notes define the calculation method.</div>
</main>
<script>function show(i,b){document.querySelectorAll('.pane').forEach((p,k)=>p.classList.toggle('active',k===i));document.querySelectorAll('.tabs button').forEach((x,k)=>x.classList.toggle('active',k===i));}</script>
</body></html>
""";
  }

  private static String lockedPage(){
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Engineering Power Systems</title><style>body{margin:0;background:#07111f;color:#e5eef8;font-family:Inter,Segoe UI,system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}.c{max-width:600px;margin:20px;padding:28px;border:1px solid #2b5068;border-radius:18px;background:#0b1f31}a{color:#67e8f9}</style></head><body><div class="c"><h1>Engineering Power Systems</h1><p>This workspace is included with Tier 2 and Tier 3. Your current account does not have the ENGINEERING_POWER_SYSTEMS feature enabled.</p><p><a href="/portal.html">Return to account portal</a></p></div></body></html>
""";
  }
}
