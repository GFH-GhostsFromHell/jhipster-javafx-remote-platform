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
        .tap-advanced{margin-top:14px}
        .tap-tabs{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:14px}
        .tap-tabs button{border:1px solid #dbe3ee;background:#fff;padding:9px 12px;border-radius:999px;cursor:pointer;font-weight:800;color:#475569}
        .tap-tabs button.active{background:#1d4ed8;color:#fff;border-color:#1d4ed8}
        .tap-pane{display:none}.tap-pane.active{display:block}
        .tap-checks{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px}
        .tap-checks label{display:flex;gap:8px;align-items:flex-start;padding:9px 10px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:9px;font-size:12px;color:#475569;line-height:1.35}
        .tap-checks input{margin-top:2px}
        .tap-callout{padding:11px 12px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:10px;color:#334155;font-size:12px;line-height:1.45;margin:10px 0}
        .tap-link{display:inline-block;margin:10px 0;padding:9px 11px;border-radius:9px;background:#eff6ff;color:#1d4ed8;font-weight:800;text-decoration:none}
        @media(max-width:680px){.tap-checks{grid-template-columns:1fr}.tap-tabs button{flex:1 1 calc(50% - 7px);min-width:0}.tap-advanced .result{min-height:260px}}
      </style>
      <script>
      (function(){
        const NL=String.fromCharCode(10);
        const report=(parts)=>parts.join(NL);
        const fmt=(v)=>Number(v).toFixed(2);
        const val=(id)=>document.getElementById(id)?.value ?? '';
        const yes=(id)=>!!document.getElementById(id)?.checked;
        function num(id){const v=parseFloat(val(id));if(!Number.isFinite(v)||v<0)throw new Error('Check '+id);return v}
        function csv(id){const a=val(id).split(',').map(x=>parseFloat(x.trim())).filter(Number.isFinite);if(!a.length||a.some(x=>x<=0))throw new Error('Check '+id);return a}
        function out(id,text){const e=document.getElementById(id);if(e)e.textContent=text}
        function run(id,fn){try{out(id,fn())}catch(e){out(id,'Check inputs: '+e.message)}}
        function phaseCurrent(kva,v,ph){return kva*1000/(v*(ph===3?Math.sqrt(3):1))}
        function starter(a){const t=[[20,'#14'],[25,'#12'],[35,'#10'],[50,'#8'],[65,'#6'],[85,'#4'],[100,'#3'],[115,'#2'],[130,'#1'],[150,'1/0'],[175,'2/0'],[200,'3/0'],[230,'4/0']];for(const [amp,size] of t)if(a<=amp)return size+' Cu (75°C starter lookup)';return 'Use the validated CEC ampacity table for this value'}
        function motorLoad(flcs){const largest=Math.max(...flcs),sum=flcs.reduce((a,b)=>a+b,0);return{largest,others:sum-largest,required:largest*1.25+sum-largest}}

        function addVisualBendEntry(){
          document.querySelectorAll('.card h3').forEach(h=>{
            if(h.textContent.trim()==='Conduit bending'&&!h.parentElement.querySelector('.visual-bend-entry')){
              const a=document.createElement('a');a.href='/bend-calcs.html';a.className='visual-bend-entry';
              a.innerHTML='Open Visual Bend Calcs →<small>Diagram-led offsets, rolling offsets, saddles, stub-ups, kicked/segmented 90s, parallel bends and obstruction layouts.</small>';
              h.parentElement.appendChild(a);
            }
          });
        }

        function calcGeneral(){run('tap_general_out',()=>{
          const mode=val('tap_general_mode'),feeder=num('tap_general_feeder'),selected=num('tap_general_selected'),len=num('tap_general_len');
          if(mode==='THREE'){
            const load=num('tap_general_load'),equipment=num('tap_general_equipment'),required=Math.max(load,equipment);
            const pass=len<=3&&yes('tap_general_enclosed')&&yes('tap_general_noextend')&&selected>=required;
            return report([
              'GENERAL 3 m EQUIPMENT TAP — RULE 14-100(1)(b)','',
              `Computed load: ${fmt(load)} A`,`Supplied equipment rating: ${fmt(equipment)} A`,`Minimum tap ampacity: ${fmt(required)} A`,`Starter conductor: ${starter(required)}`,'',
              `Length: ${fmt(len)} m → ${len<=3?'PASS':'FAIL'}`,
              `Enclosed wiring condition: ${yes('tap_general_enclosed')?'PASS':'FAIL'}`,
              `Does not extend beyond supplied equipment: ${yes('tap_general_noextend')?'PASS':'FAIL'}`,
              `Selected tap ampacity: ${fmt(selected)} A → ${selected>=required?'PASS':'FAIL'}`,'',
              `OVERALL: ${pass?'PASS':'DOES NOT MEET THIS TAP EXCEPTION'}`,'',
              'The 3 m equipment exception is load/equipment-rating based; it is not automatically a one-third calculation.'
            ]);
          }
          const down=num('tap_general_down'),reduced=len<=7.5&&yes('tap_general_mech'),required=reduced?feeder/3:feeder;
          const pass=selected>=required&&(!reduced||down<=selected);
          return report([
            'GENERAL 7.5 m ONE-THIRD TAP — RULE 14-100(1)(c)','',
            `Larger conductor ampacity: ${fmt(feeder)} A`,`One-third floor: ${fmt(feeder/3)} A`,`Required tap ampacity: ${fmt(required)} A`,`Starter conductor: ${starter(required)}`,'',
            `Length: ${fmt(len)} m`,`Mechanical protection: ${yes('tap_general_mech')?'YES':'NO'}`,
            `Reduced-tap exception available: ${reduced?'YES':'NO — full feeder ampacity required'}`,
            `Selected tap ampacity: ${fmt(selected)} A → ${selected>=required?'PASS':'FAIL'}`,
            `Downstream single OCPD: ${fmt(down)} A${reduced?' → '+(down<=selected?'PASS':'FAIL — exceeds tap ampacity'):''}`,'',
            `OVERALL: ${pass?'PASS':'CHECK / FAIL'}`,'','Use the ampacity of the larger conductor, not merely the breaker rating.'
          ]);
        })}

        function calcTransformer(){run('tap_tx_out',()=>{
          const feeder=num('tap_tx_feeder'),kva=num('tap_tx_kva'),pv=num('tap_tx_pv'),sv=num('tap_tx_sv'),ph=parseInt(val('tap_tx_ph'),10);
          const pSel=num('tap_tx_pa'),sSel=num('tap_tx_sa'),pLen=num('tap_tx_plen'),sLen=num('tap_tx_slen'),down=num('tap_tx_down');
          const ip=phaseCurrent(kva,pv,ph),isec=phaseCurrent(kva,sv,ph),ratio=pv/sv;
          const pDemand=num('tap_tx_pdemand'),sDemand=num('tap_tx_sdemand');
          const pLoad=pDemand>0?pDemand:ip*1.25,sLoad=sDemand>0?sDemand:isec*1.25;
          const pReq=Math.max(feeder/3,pLoad),sRatio=pSel*ratio,sReq=Math.max(sRatio,sLoad),total=pLen+sLen;
          const pOk=pSel>=pReq,sOk=sSel>=sReq,lenOk=total<=7.5,mech=yes('tap_tx_mech'),downOk=down<=sSel,pass=pOk&&sOk&&lenOk&&mech&&downOk;
          return report([
            'TRANSFORMER TAP — RULE 14-100(1)(f) + RULE 26-256','',
            `Transformer: ${fmt(kva)} kVA, ${ph}φ, ${fmt(pv)} V → ${fmt(sv)} V`,`Primary rated current: ${fmt(ip)} A`,`Secondary rated current: ${fmt(isec)} A`,`Voltage ratio Vp/Vs: ${ratio.toFixed(3)}`,'',
            'PRIMARY TAP',`Normal 125% primary basis: ${fmt(ip*1.25)} A`,`Entered demand override: ${pDemand>0?fmt(pDemand)+' A':'not used'}`,`One-third feeder floor: ${fmt(feeder/3)} A`,`Required primary ampacity: ${fmt(pReq)} A`,`Selected primary tap: ${fmt(pSel)} A → ${pOk?'PASS':'FAIL'}`,`Starter: ${starter(pReq)}`,'',
            'SECONDARY TAP',`Normal 125% secondary basis: ${fmt(isec*1.25)} A`,`Entered demand override: ${sDemand>0?fmt(sDemand)+' A':'not used'}`,`Primary tap × voltage ratio: ${fmt(sRatio)} A`,`Required secondary ampacity: ${fmt(sReq)} A`,`Selected secondary conductor: ${fmt(sSel)} A → ${sOk?'PASS':'FAIL'}`,`Starter: ${starter(sReq)}`,'',
            'LENGTH / PROTECTION',`Primary + secondary length: ${fmt(total)} m → ${lenOk?'PASS':'FAIL — exceeds 7.5 m combined limit'}`,`Mechanical protection: ${mech?'PASS':'FAIL'}`,`Secondary terminating OCPD: ${fmt(down)} A → ${downOk?'PASS':'FAIL — exceeds secondary ampacity'}`,'',
            `OVERALL: ${pass?'PASS':'CHECK / FAIL'}`,'','Demand overrides are for installations where Rule 26-256 permits demand-load sizing and Rules 14-100/14-104 provide the required protection. Otherwise leave them at 0 and use the 125% basis.'
          ]);
        })}

        function calcMotor(){run('tap_motor_out',()=>{
          const mode=val('tap_motor_mode'),base=num('tap_motor_base'),selected=num('tap_motor_selected'),len=num('tap_motor_len'),flcs=csv('tap_motor_flcs');
          const load=motorLoad(flcs);let required,route;
          if(mode==='GROUP'){
            const single=flcs[0]*1.25;
            if(len<=7.5){required=Math.max(single,base/3);route='Rule 28-106(3): group branch-circuit tap ≤7.5 m'}
            else{required=base;route='Tap exceeds 7.5 m: branch-conductor ampacity required'}
          }else{
            if(len<=3&&yes('tap_motor_metal')){required=load.required;route='Rule 28-110(2)(a): ≤3 m and enclosed in metal'}
            else if(len<=7.5&&yes('tap_motor_mech')){required=Math.max(load.required,base/3);route='Rule 28-110(2)(b): ≤7.5 m, ≥1/3 feeder and mechanically protected'}
            else{required=base;route='Reduced feeder-tap exception unavailable: full feeder ampacity required'}
          }
          const pass=selected>=required;
          return report([
            `MOTOR TAP — ${route}`,'',`Entered FLC(s): ${flcs.map(fmt).join(', ')} A`,`Largest motor FLC: ${fmt(load.largest)} A`,`Continuous-duty motor load basis: ${fmt(load.required)} A`,`Source ${mode==='GROUP'?'branch':'feeder'} conductor ampacity: ${fmt(base)} A`,`One-third value: ${fmt(base/3)} A`,'',
            `Required tap ampacity: ${fmt(required)} A`,`Starter conductor: ${starter(required)}`,`Selected tap ampacity: ${fmt(selected)} A → ${pass?'PASS':'FAIL'}`,`Tap length: ${fmt(len)} m`,'',`OVERALL: ${pass?'PASS':'CHECK / FAIL'}`,'','For non-continuous-duty motors, replace the 125% basis with the applicable Table 27 calculation. Motor branch OCPD remains a separate Section 28 calculation.'
          ]);
        })}

        function calcWelder(){run('tap_w_out',()=>{
          const type=val('tap_w_type'),rated=num('tap_w_rated'),actual=num('tap_w_actual'),duty=num('tap_w_duty'),feeder=num('tap_w_feeder'),selected=num('tap_w_selected'),len=num('tap_w_len'),down=num('tap_w_down');
          let load,label,maxFactor=null;
          if(type==='TRANSFORMER'){load=rated*duty;label='Transformer/inverter: rated primary current × entered Table 42A factor';maxFactor=2}
          else if(type==='MOTOR'){load=rated*duty;label='Motor-generator: rated primary current × entered Table 42B factor; Section 28 also applies'}
          else if(type==='RES_MANUAL'){load=rated*0.50;label='Manual non-automatic resistance welder: 50% of rated primary current';maxFactor=3}
          else if(type==='RES_AUTO'){load=rated*0.70;label='Seam/automatically fed resistance welder: 70% of rated primary current';maxFactor=3}
          else{load=actual*duty;label='Known actual primary current × entered Table 42C factor';maxFactor=3}
          const reduced=len<=7.5&&yes('tap_w_mech'),required=reduced?Math.max(load,feeder/3):feeder;
          const tapOk=selected>=required,terminalOk=!reduced||down<=selected;
          const sectionMax=maxFactor===null?null:Math.min(rated*maxFactor,selected*maxFactor),sectionOk=sectionMax===null||down<=sectionMax;
          const pass=tapOk&&terminalOk&&sectionOk;
          return report([
            'WELDER SUPPLY TAP — SECTION 42 + GENERAL TAP RULE','',label,`Welder supply-conductor load: ${fmt(load)} A`,`Feeder conductor ampacity: ${fmt(feeder)} A`,`One-third feeder value: ${fmt(feeder/3)} A`,'',
            `Reduced general tap available: ${reduced?'YES — Rule 14-100(1)(c) conditions entered':'NO — full feeder ampacity required'}`,`Required tap ampacity: ${fmt(required)} A`,`Starter conductor: ${starter(required)}`,`Selected tap ampacity: ${fmt(selected)} A → ${tapOk?'PASS':'FAIL'}`,`Downstream OCPD: ${fmt(down)} A${reduced?' → '+(terminalOk?'PASS':'FAIL — the 7.5 m general tap must terminate in OCPD ≤ tap ampacity'):''}`,
            sectionMax===null?'Motor-generator welder: verify the Motor Tap worksheet / Section 28 conditions.':`Section 42 worksheet OCPD ceiling before permitted next-size exceptions: ${fmt(sectionMax)} A → ${sectionOk?'PASS':'CHECK'}`,'',
            `OVERALL: ${pass?'PASS':'CHECK / FAIL'}`,'','Section 42 does not create a separate reduced-size tap exception. Calculate the welder supply conductor first, then satisfy the applicable Section 14 tap conditions. The 3 m equipment-tap route can be checked on the General Tap tab.'
          ]);
        })}

        function pane(title,body,outId,label,handler){return `<div class="tap-pane"><div class="workspace"><div><div class="card"><h3>${title}</h3>${body}</div><button class="btn tap-calc" type="button" data-handler="${handler}">${label}</button></div><pre class="result" id="${outId}">Enter the question facts, then calculate.</pre></div></div>`}
        function addTapCalculators(){
          const section=document.getElementById('s14');if(!section||section.querySelector('.tap-advanced'))return;
          const oldTabs=section.querySelector('.tabs');if(oldTabs)oldTabs.style.display='none';section.querySelectorAll('.pane').forEach(p=>p.style.display='none');
          const lead=section.querySelector('.lead');if(lead)lead.textContent='CEC 2024 tap-conductor worksheets: general taps, transformer primary/secondary taps, motor taps and welder supply taps.';
          const wrap=document.createElement('div');wrap.className='tap-advanced';
          const general=`<div class="grid"><div class="field wide"><label>Tap rule mode</label><select id="tap_general_mode"><option value="SEVEN">7.5 m one-third tap — Rule 14-100(1)(c)</option><option value="THREE">3 m equipment tap — Rule 14-100(1)(b)</option></select></div><div class="field"><label>Larger feeder conductor ampacity A</label><input id="tap_general_feeder" type="number" value="285"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_general_selected" type="number" value="100"></div><div class="field"><label>Tap length m</label><input id="tap_general_len" type="number" step="0.1" value="7.5"></div><div class="field"><label>Downstream single OCPD A (7.5 m)</label><input id="tap_general_down" type="number" value="100"></div><div class="field"><label>Computed load A (3 m)</label><input id="tap_general_load" type="number" value="80"></div><div class="field"><label>Supplied equipment rating A (3 m)</label><input id="tap_general_equipment" type="number" value="100"></div></div><div class="tap-checks"><label><input id="tap_general_mech" type="checkbox" checked>Mechanically protected for the 7.5 m one-third tap.</label><label><input id="tap_general_enclosed" type="checkbox" checked>3 m tap is enclosed in the required wiring method.</label><label><input id="tap_general_noextend" type="checkbox" checked>3 m tap does not extend beyond the equipment it supplies.</label></div><div class="tap-callout">Use conductor ampacity, not just breaker rating, for the one-third comparison.</div>`;
          const tx=`<div class="grid"><div class="field"><label>Larger feeder conductor ampacity A</label><input id="tap_tx_feeder" type="number" value="300"></div><div class="field"><label>Transformer kVA</label><input id="tap_tx_kva" type="number" value="75"></div><div class="field"><label>Primary voltage V</label><input id="tap_tx_pv" type="number" value="600"></div><div class="field"><label>Secondary voltage V</label><input id="tap_tx_sv" type="number" value="208"></div><div class="field"><label>Phases</label><select id="tap_tx_ph"><option value="3">3</option><option value="1">1</option></select></div><div class="field"><label>Primary demand-load override A (0 = 125%)</label><input id="tap_tx_pdemand" type="number" value="0"></div><div class="field"><label>Secondary demand-load override A (0 = 125%)</label><input id="tap_tx_sdemand" type="number" value="0"></div><div class="field"><label>Selected primary tap ampacity A</label><input id="tap_tx_pa" type="number" value="100"></div><div class="field"><label>Selected secondary conductor ampacity A</label><input id="tap_tx_sa" type="number" value="350"></div><div class="field"><label>Primary tap length m</label><input id="tap_tx_plen" type="number" step="0.1" value="3"></div><div class="field"><label>Secondary conductor length m</label><input id="tap_tx_slen" type="number" step="0.1" value="4"></div><div class="field"><label>Secondary terminating OCPD A</label><input id="tap_tx_down" type="number" value="350"></div></div><div class="tap-checks"><label><input id="tap_tx_mech" type="checkbox" checked>Primary and secondary tap conductors are protected from mechanical damage.</label></div><div class="tap-callout">Checks the one-third primary floor, voltage-ratio secondary floor, transformer load basis, combined 7.5 m length, mechanical protection and the single secondary OCPD.</div>`;
          const motor=`<div class="grid"><div class="field wide"><label>Motor tap mode</label><select id="tap_motor_mode"><option value="FEEDER">Feeder tap to motor branch OCPD — Rule 28-110(2)</option><option value="GROUP">Group branch circuit tap to individual motor — Rule 28-106(3)</option></select></div><div class="field"><label>Feeder / branch conductor ampacity A</label><input id="tap_motor_base" type="number" value="200"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_motor_selected" type="number" value="85"></div><div class="field wide"><label>Motor FLC(s) A, comma-separated</label><input id="tap_motor_flcs" value="40"></div><div class="field"><label>Tap length m</label><input id="tap_motor_len" type="number" step="0.1" value="7.5"></div></div><div class="tap-checks"><label><input id="tap_motor_metal" type="checkbox">Tap is ≤3 m and enclosed in metal.</label><label><input id="tap_motor_mech" type="checkbox" checked>Tap is suitably protected from mechanical damage.</label></div><div class="tap-callout">Continuous-duty worksheet uses 125% of the largest FLC plus the other motor FLCs. Non-continuous duty requires the applicable Table 27 percentage.</div>`;
          const welder=`<div class="grid"><div class="field wide"><label>Welder type</label><select id="tap_w_type"><option value="TRANSFORMER">Transformer / inverter — Table 42A factor</option><option value="MOTOR">Motor-generator — Table 42B factor + Section 28</option><option value="RES_MANUAL">Resistance, manual non-automatic — 50%</option><option value="RES_AUTO">Resistance, seam / automatic — 70%</option><option value="RES_KNOWN">Resistance, known actual current — Table 42C factor</option></select></div><div class="field"><label>Rated primary current A</label><input id="tap_w_rated" type="number" value="100"></div><div class="field"><label>Actual primary current A (known-current mode)</label><input id="tap_w_actual" type="number" value="100"></div><div class="field"><label>Duty-cycle / table factor</label><input id="tap_w_duty" type="number" step="0.01" value="0.63"></div><div class="field"><label>Feeder conductor ampacity A</label><input id="tap_w_feeder" type="number" value="200"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_w_selected" type="number" value="85"></div><div class="field"><label>Tap length m</label><input id="tap_w_len" type="number" step="0.1" value="7.5"></div><div class="field"><label>Downstream welder OCPD A</label><input id="tap_w_down" type="number" value="80"></div></div><div class="tap-checks"><label><input id="tap_w_mech" type="checkbox" checked>Tap is suitably protected from mechanical damage for the 7.5 m general tap exception.</label></div><div class="tap-callout">Section 42 sizes the welder supply conductor; a reduced tap still has to satisfy the applicable general tap rule. Motor-generator welders also invoke Section 28.</div>`;
          wrap.innerHTML=`<div class="tap-tabs"><button type="button" class="active">General Tap</button><button type="button">Transformer Tap</button><button type="button">Motor Tap</button><button type="button">Welder Tap</button></div>${pane('General feeder / splitter tap',general,'tap_general_out','Calculate general tap','general')}${pane('Transformer primary + secondary tap',tx,'tap_tx_out','Calculate transformer tap','transformer')}${pane('Motor tap conductor',motor,'tap_motor_out','Calculate motor tap','motor')}${pane('Welder supply tap',welder,'tap_w_out','Calculate welder tap','welder')}`;
          section.appendChild(wrap);
          const panes=[...wrap.querySelectorAll('.tap-pane')],tabs=[...wrap.querySelectorAll('.tap-tabs button')];panes[0].classList.add('active');
          tabs.forEach((button,index)=>button.addEventListener('click',()=>{tabs.forEach(x=>x.classList.remove('active'));panes.forEach(x=>x.classList.remove('active'));button.classList.add('active');panes[index].classList.add('active')}));
          wrap.querySelector('[data-handler="general"]').addEventListener('click',calcGeneral);
          wrap.querySelector('[data-handler="transformer"]').addEventListener('click',calcTransformer);
          wrap.querySelector('[data-handler="motor"]').addEventListener('click',calcMotor);
          wrap.querySelector('[data-handler="welder"]').addEventListener('click',calcWelder);
          const wLead=document.querySelector('#s42 .lead');if(wLead&&!document.querySelector('#s42 .tap-link')){const a=document.createElement('a');a.href='#';a.className='tap-link';a.textContent='Open Section 14 tap-conductor calculators →';a.addEventListener('click',e=>{e.preventDefault();const b=[...document.querySelectorAll('.nav button')].find(x=>x.textContent.includes('Section 14'));if(b)b.click()});wLead.parentElement.insertBefore(a,wLead.nextSibling)}
        }

        function init(){addVisualBendEntry();addTapCalculators()}
        if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(init,0));else setTimeout(init,0);setTimeout(init,250);
      })();
      </script>
      """;

    return html.replace("</body>", enhancement + "</body>");
  }
}
