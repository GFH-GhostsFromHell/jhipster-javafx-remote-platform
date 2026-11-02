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
:root{font-family:Inter,Segoe UI,system-ui,sans-serif;color:#e5eef8;background:#07111f}*{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#07111f,#0b2743 48%,#10263a);min-height:100vh}.top{position:sticky;top:0;z-index:5;display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 20px;background:#07111ff2;border-bottom:1px solid #25506d}.top strong{font-size:18px}.top a{color:#dff8ff;text-decoration:none;border:1px solid #2d607e;border-radius:9px;padding:8px 11px;margin-left:6px}.wrap{max-width:1250px;margin:auto;padding:24px}.hero{padding:26px;border:1px solid #2f607c;border-radius:18px;background:linear-gradient(135deg,#10263a,#0e4b68);box-shadow:0 18px 45px #0005}.eyebrow{font-size:11px;font-weight:800;letter-spacing:.12em;color:#67e8f9}.hero h1{margin:7px 0 8px;font-size:30px}.hero p{margin:0;max-width:900px;color:#c6d4e2;line-height:1.55}.tier{display:inline-block;margin-top:14px;padding:6px 10px;border-radius:999px;background:#0e7490;color:white;font-size:12px;font-weight:800}.tabs{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0}.tabs button{border:1px solid #32627e;background:#10263a;color:#dbeafe;border-radius:999px;padding:9px 13px;font-weight:800;cursor:pointer}.tabs button.active{background:#0e7490;border-color:#22d3ee;color:white}.pane{display:none}.pane.active{display:block}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.card{background:#0b1f31;border:1px solid #2b5068;border-radius:16px;padding:18px;box-shadow:0 8px 24px #0002}.card h2,.card h3{margin-top:0}.card p,.card li{color:#b7c8d8;line-height:1.5}.status{font-size:11px;font-weight:850;color:#67e8f9;text-transform:uppercase;letter-spacing:.08em}.calc{padding:13px;border:1px dashed #3d6c87;border-radius:12px;background:#0b2235;margin-top:10px}.calc b{display:block;color:#e7f7ff;margin-bottom:4px}.calc span{color:#9eb3c5;font-size:13px}.note{margin-top:18px;padding:16px;border-radius:13px;background:#142d40;border:1px solid #35627c;color:#d6e5f0}.note strong{color:#67e8f9}.wide{grid-column:1/-1}.formgrid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.field label{display:block;color:#b7c8d8;font-size:12px;font-weight:800;margin-bottom:5px}.field input,.field select{width:100%;padding:10px 11px;border:1px solid #3a6680;border-radius:9px;background:#071827;color:#eaf7ff;font:inherit}.field small{display:block;color:#7992a7;margin-top:4px}.calcbtn{margin-top:14px;padding:11px 15px;border:0;border-radius:10px;background:linear-gradient(90deg,#1d4ed8,#0e7490);color:white;font-weight:850;cursor:pointer}.result{margin-top:14px;padding:15px;border:1px solid #39708e;border-radius:12px;background:#071827}.result-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.metric{padding:10px;border-radius:10px;background:#102b40;border:1px solid #244f68}.metric span{display:block;color:#8fa9bc;font-size:11px;text-transform:uppercase;font-weight:800}.metric b{display:block;margin-top:4px;color:#e7f7ff;font-size:17px}.steps{margin-top:12px;color:#a9bdcc;font-size:13px;line-height:1.55;white-space:pre-line}.warn{margin-top:12px;color:#f6d48a;font-size:12px;line-height:1.5}@media(max-width:900px){.formgrid,.result-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.grid,.formgrid,.result-grid{grid-template-columns:1fr}.wrap{padding:14px}.hero h1{font-size:25px}.top{align-items:flex-start;flex-direction:column}.top div:last-child{display:flex;flex-wrap:wrap}.top a{margin:0 6px 0 0}}
</style>
</head>
<body>
<header class="top"><strong>⚡ Engineering Power Systems</strong><div><a href="/calculators.html">CEC Workbench</a><a href="/portal.html">Portal</a></div></header>
<main class="wrap">
<section class="hero"><div class="eyebrow">ENGINEERING · POWER SYSTEMS</div><h1>Transmission, Distribution, Protection & Controls</h1><p>This workspace is being built around the engineering methods and examples used in your notes. The short-circuit calculator below uses the transformer-impedance and point-to-point feeder method we previously worked through; the remaining calculation slots stay notes-driven.</p><span class="tier">Included with Tier 2 and Tier 3</span></section>
<div class="tabs"><button class="active" onclick="show(0,this)">Transmission & Distribution</button><button onclick="show(1,this)">Protection & Controls</button><button onclick="show(2,this)">Calculation Library</button></div>
<section class="pane active">
  <div class="grid">
    <article class="card"><div class="status">Workspace ready</div><h2>Transmission</h2><p>Reserved for line and bulk-power calculations.</p>
      <div class="calc"><b>3-phase line loading / power flow</b><span>Awaiting your notes and preferred method.</span></div>
      <div class="calc"><b>Line impedance, losses & efficiency</b><span>Awaiting your notes and conductor/model assumptions.</span></div>
      <div class="calc"><b>Voltage regulation / receiving-end voltage</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Per-unit system</b><span>Awaiting base-value and conversion notes.</span></div>
    </article>
    <article class="card"><div class="status">Workspace ready</div><h2>Distribution</h2><p>Substations, feeders and distribution-system studies.</p>
      <div class="calc"><b>Feeder loading & voltage profile</b><span>Awaiting your notes.</span></div>
      <div class="calc"><b>Transformer / substation loading</b><span>Awaiting your engineering calculation workflow.</span></div>
      <div class="calc"><b>Short-circuit / fault-current study</b><span>Calculator active below.</span></div>
      <div class="calc"><b>Demand, diversity & load allocation</b><span>Awaiting your notes.</span></div>
    </article>

    <article class="card wide" id="faultCurrentCard"><div class="status">Active calculator</div><h2>Short-Circuit / Available Fault Current</h2><p>Calculates transformer-terminal RMS symmetrical fault current from transformer kVA, secondary voltage and percent impedance. It can also apply the point-to-point feeder reduction method we used to estimate available fault current downstream.</p>
      <div class="formgrid">
        <div class="field"><label>System phase</label><select id="faultPhase"><option value="3">3-phase</option><option value="1">1-phase</option></select></div>
        <div class="field"><label>Transformer size (kVA)</label><input id="faultKva" type="number" step="any" value="500"></div>
        <div class="field"><label>Secondary voltage (V)</label><input id="faultVolts" type="number" step="any" value="208"><small>Use line-to-line voltage for 3-phase.</small></div>
        <div class="field"><label>Nameplate impedance (%)</label><input id="faultZ" type="number" step="any" value="5"></div>
        <div class="field"><label>Impedance used</label><select id="faultTolerance"><option value="0.90">90% of nameplate Z — maximum fault current</option><option value="1.00">100% of nameplate Z — nominal</option></select><small>Matches the 90% minimum-impedance method used in our examples.</small></div>
        <div class="field"><label>Downstream feeder length (ft)</label><input id="faultLength" type="number" step="any" value="35"><small>Enter 0 to show transformer-terminal current only.</small></div>
        <div class="field"><label>Conductor C value</label><input id="faultC" type="number" step="any" value="19703"><small>Use the C value for the conductor/raceway method from your source material.</small></div>
        <div class="field"><label>Parallel conductors per phase (n)</label><input id="faultN" type="number" step="1" min="1" value="1"></div>
      </div>
      <button class="calcbtn" onclick="calcFault()">Calculate fault current</button>
      <div class="result" id="faultResult"><div class="result-grid"><div class="metric"><span>Transformer FLA</span><b id="rFla">—</b></div><div class="metric"><span>Impedance used</span><b id="rZ">—</b></div><div class="metric"><span>Fault multiplier</span><b id="rMult">—</b></div><div class="metric"><span>Terminal fault current</span><b id="rTerminal">—</b></div><div class="metric"><span>Fault level</span><b id="rMva">—</b></div><div class="metric"><span>Downstream fault current</span><b id="rDownstream">—</b></div></div><div class="steps" id="faultSteps">Enter the transformer and feeder information, then calculate.</div><div class="warn">Assumes an infinite primary source for the transformer-terminal calculation and ignores upstream source impedance unless separately accounted for. Downstream feeder reduction currently follows the 3-phase point-to-point method used in our prior examples. Final equipment interrupting/SCCR decisions must use verified project and utility data.</div></div>
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
    <article class="card"><div class="status">Implemented method</div><h2>Short-circuit method</h2><p>Transformer-terminal fault current now follows the same method used in the prior worked examples: full-load current, minimum/nominal percent impedance, fault-current multiplier and optional downstream point-to-point feeder multiplier.</p></article>
  </div>
</section>
<div class="note"><strong>Current status:</strong> short-circuit / available fault-current calculation is active. The other engineering calculators remain placeholders until your notes define their methods.</div>
</main>
<script>
function show(i,b){document.querySelectorAll('.pane').forEach((p,k)=>p.classList.toggle('active',k===i));document.querySelectorAll('.tabs button').forEach((x,k)=>x.classList.toggle('active',k===i));}
function num(id){return Number(document.getElementById(id).value);}
function amps(v){if(!Number.isFinite(v))return '—';return v>=1000?(v/1000).toFixed(2)+' kA':v.toFixed(1)+' A';}
function calcFault(){
  const phase=num('faultPhase'), kva=num('faultKva'), volts=num('faultVolts'), zName=num('faultZ'), zFactor=num('faultTolerance'), length=num('faultLength'), c=num('faultC'), n=num('faultN');
  if(kva<=0||volts<=0||zName<=0||zFactor<=0||n<=0){document.getElementById('faultSteps').textContent='Enter positive transformer kVA, voltage, impedance and parallel-conductor values.';return;}
  const fla=phase===3?(kva*1000)/(1.73*volts):(kva*1000)/volts;
  const zUsed=zName*zFactor;
  const multiplier=100/zUsed;
  const terminal=fla*multiplier;
  const faultMva=phase===3?(1.73*volts*terminal)/1000000:(volts*terminal)/1000000;
  let downstream=terminal, feederText='No feeder reduction applied.';
  if(length>0){
    if(phase!==3){
      feederText='Downstream point-to-point reduction is currently enabled for the 3-phase method only; terminal fault current is shown.';
    } else if(c<=0){
      feederText='Enter a positive conductor C value to calculate downstream fault current.';
    } else {
      const f=(1.73*length*terminal)/(c*n*volts);
      const m=1/(1+f);
      downstream=terminal*m;
      feederText='Point-to-point feeder: f = (1.73 × L × Isc) / (C × n × V) = '+f.toFixed(4)+'; M = 1 / (1 + f) = '+m.toFixed(4)+'; downstream Isc = '+amps(downstream)+'.';
    }
  }
  document.getElementById('rFla').textContent=amps(fla);
  document.getElementById('rZ').textContent=zUsed.toFixed(3)+' %';
  document.getElementById('rMult').textContent=multiplier.toFixed(3)+' ×';
  document.getElementById('rTerminal').textContent=amps(terminal);
  document.getElementById('rMva').textContent=faultMva.toFixed(3)+' MVA';
  document.getElementById('rDownstream').textContent=amps(downstream);
  const flaFormula=phase===3?'I_FL = kVA × 1000 / (1.73 × V)':'I_FL = kVA × 1000 / V';
  const nl=String.fromCharCode(10);
  document.getElementById('faultSteps').textContent=flaFormula+' = '+amps(fla)+nl+'Z_used = '+zName.toFixed(3)+'% × '+zFactor.toFixed(2)+' = '+zUsed.toFixed(3)+'%'+nl+'Fault multiplier = 100 / '+zUsed.toFixed(3)+' = '+multiplier.toFixed(3)+' ×'+nl+'Isc at transformer terminals = '+amps(fla)+' × '+multiplier.toFixed(3)+' = '+amps(terminal)+nl+feederText;
}
calcFault();
</script>
</body></html>
""";
  }

  private static String lockedPage(){
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Engineering Power Systems</title><style>body{margin:0;background:#07111f;color:#e5eef8;font-family:Inter,Segoe UI,system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}.c{max-width:600px;margin:20px;padding:28px;border:1px solid #2b5068;border-radius:18px;background:#0b1f31}a{color:#67e8f9}</style></head><body><div class="c"><h1>Engineering Power Systems</h1><p>This workspace is included with Tier 2 and Tier 3. Your current account does not have the ENGINEERING_POWER_SYSTEMS feature enabled.</p><p><a href="/portal.html">Return to account portal</a></p></div></body></html>
""";
  }
}
