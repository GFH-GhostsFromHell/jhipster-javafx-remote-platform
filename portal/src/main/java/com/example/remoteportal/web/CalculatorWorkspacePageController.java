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
        .tap-advanced .tap-tabs{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:14px}
        .tap-advanced .tap-tabs button{border:1px solid #dbe3ee;background:#fff;padding:9px 12px;border-radius:999px;cursor:pointer;font-weight:800;color:#475569}
        .tap-advanced .tap-tabs button.active{background:#1d4ed8;color:#fff;border-color:#1d4ed8}
        .tap-advanced .tap-pane{display:none}
        .tap-advanced .tap-pane.active{display:block}
        .tap-checks{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px}
        .tap-checks label{display:flex;gap:8px;align-items:flex-start;padding:9px 10px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:9px;font-size:12px;color:#475569;line-height:1.35}
        .tap-checks input{margin-top:2px}
        .tap-callout{padding:11px 12px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:10px;color:#334155;font-size:12px;line-height:1.45;margin:10px 0}
        .tap-link{display:inline-block;margin-top:10px;padding:9px 11px;border-radius:9px;background:#eff6ff;color:#1d4ed8;font-weight:800;text-decoration:none}
        @media(max-width:680px){.tap-checks{grid-template-columns:1fr}.tap-advanced .tap-tabs button{flex:1 1 calc(50% - 7px);min-width:0}.tap-advanced .result{min-height:260px}}
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

        function num(id){
          var el=document.getElementById(id), v=parseFloat(el && el.value);
          if(!Number.isFinite(v) || v<0) throw new Error('Check '+id);
          return v;
        }
        function checked(id){var el=document.getElementById(id);return !!(el && el.checked)}
        function value(id){var el=document.getElementById(id);return el?el.value:''}
        function fmt(v){return Number(v).toFixed(2)}
        function phaseCurrent(kva,voltage,phases){return kva*1000/(voltage*(phases===3?Math.sqrt(3):1))}
        function starter(amps){
          var t=[[20,'#14'],[25,'#12'],[35,'#10'],[50,'#8'],[65,'#6'],[85,'#4'],[100,'#3'],[115,'#2'],[130,'#1'],[150,'1/0'],[175,'2/0'],[200,'3/0'],[230,'4/0']];
          for(var k=0;k<t.length;k++) if(amps<=t[k][0]) return t[k][1]+' Cu (75°C starter lookup)';
          return 'Use the validated CEC ampacity table for this value';
        }
        function output(id,text){var e=document.getElementById(id);if(e)e.textContent=text}
        function run(id,fn){try{output(id,fn())}catch(err){output(id,'Check inputs: '+err.message)}}
        function csv(id){
          var vals=value(id).split(',').map(function(x){return parseFloat(x.trim())}).filter(Number.isFinite);
          if(!vals.length || vals.some(function(x){return x<=0})) throw new Error(id);
          return vals;
        }
        function motorLoad(flcs){
          var largest=Math.max.apply(null,flcs), sum=flcs.reduce(function(a,b){return a+b},0);
          return {largest:largest,others:sum-largest,required:largest*1.25+(sum-largest)};
        }

        function calcGeneralTap(){run('tap_general_out',function(){
          var mode=value('tap_general_mode'), feeder=num('tap_general_feeder'), selected=num('tap_general_selected'), len=num('tap_general_len');
          if(mode==='THREE_METRE'){
            var load=num('tap_general_load'), equipment=num('tap_general_equipment');
            var required=Math.max(load,equipment);
            var conditions=len<=3 && checked('tap_general_enclosed') && checked('tap_general_noextend');
            var pass=selected>=required && conditions;
            return 'GENERAL 3 m EQUIPMENT TAP — RULE 14-100(1)(b)\n\nComputed load: '+fmt(load)+' A\nSupplied equipment rating: '+fmt(equipment)+' A\nMinimum tap ampacity: '+fmt(required)+' A\nStarter conductor: '+starter(required)+'\n\nLength: '+fmt(len)+' m → '+(len<=3?'PASS':'FAIL')+'\nEnclosed wiring condition: '+(checked('tap_general_enclosed')?'PASS':'FAIL')+'\nDoes not extend beyond supplied equipment: '+(checked('tap_general_noextend')?'PASS':'FAIL')+'\nSelected tap ampacity: '+fmt(selected)+' A → '+(selected>=required?'PASS':'FAIL')+'\n\nOVERALL: '+(pass?'PASS':'DOES NOT MEET THIS TAP EXCEPTION')+'\n\nThis 3 m exception uses the computed load/equipment rating; it is not automatically a one-third tap.';
          }
          var down=num('tap_general_down'), mech=checked('tap_general_mech');
          var reduced=len<=7.5 && mech, required=reduced?feeder/3:feeder;
          var pass=selected>=required && (!reduced || down<=selected);
          return 'GENERAL 7.5 m ONE-THIRD TAP — RULE 14-100(1)(c)\n\nLarger conductor ampacity: '+fmt(feeder)+' A\nOne-third floor: '+fmt(feeder/3)+' A\nRequired tap ampacity for entered conditions: '+fmt(required)+' A\nStarter conductor: '+starter(required)+'\n\nLength: '+fmt(len)+' m\nMechanical protection: '+(mech?'YES':'NO')+'\nReduced-tap exception available: '+(reduced?'YES':'NO — full feeder ampacity shown')+'\nSelected tap ampacity: '+fmt(selected)+' A → '+(selected>=required?'PASS':'FAIL')+'\nDownstream single OCPD: '+fmt(down)+' A'+(reduced?' → '+(down<=selected?'PASS':'FAIL — exceeds tap ampacity'):'')+'\n\nOVERALL: '+(pass?'PASS':'CHECK / FAIL')+'\n\nUse conductor ampacity of the larger conductor, not merely its breaker rating.';
        })}

        function calcTransformerTap(){run('tap_transformer_out',function(){
          var feeder=num('tap_tx_feeder'), kva=num('tap_tx_kva'), pv=num('tap_tx_pv'), sv=num('tap_tx_sv'), ph=parseInt(value('tap_tx_ph'),10);
          var pSel=num('tap_tx_pa'), sSel=num('tap_tx_sa'), pLen=num('tap_tx_plen'), sLen=num('tap_tx_slen'), down=num('tap_tx_down');
          var ip=phaseCurrent(kva,pv,ph), isec=phaseCurrent(kva,sv,ph), ratio=pv/sv;
          var pFloor=Math.max(feeder/3,ip), sFloor=Math.max(isec,pSel*ratio), total=pLen+sLen;
          var pOk=pSel>=pFloor, sOk=sSel>=sFloor, lenOk=total<=7.5, mech=checked('tap_tx_mech'), downOk=down<=sSel;
          var pass=pOk&&sOk&&lenOk&&mech&&downOk;
          return 'TRANSFORMER TAP — RULE 14-100(1)(f)\n\nTransformer: '+fmt(kva)+' kVA, '+ph+'φ, '+fmt(pv)+' V → '+fmt(sv)+' V\nPrimary rated current: '+fmt(ip)+' A\nSecondary rated current: '+fmt(isec)+' A\nVoltage ratio Vp/Vs: '+ratio.toFixed(3)+'\n\nPRIMARY TAP\nLarger feeder conductor ampacity: '+fmt(feeder)+' A\nOne-third feeder floor: '+fmt(feeder/3)+' A\nWorksheet primary minimum (greater of 1/3 feeder and rated primary current): '+fmt(pFloor)+' A\nSelected primary tap: '+fmt(pSel)+' A → '+(pOk?'PASS':'FAIL')+'\nStarter: '+starter(pFloor)+'\n\nSECONDARY TAP\nRule 14-100(1)(f)(ii) floor from selected primary tap × voltage ratio: '+fmt(pSel*ratio)+' A\nRated-secondary-current check: '+fmt(isec)+' A\nRequired secondary ampacity: '+fmt(sFloor)+' A\nSelected secondary conductor: '+fmt(sSel)+' A → '+(sOk?'PASS':'FAIL')+'\nStarter: '+starter(sFloor)+'\n\nLENGTH / PROTECTION\nPrimary + secondary length: '+fmt(total)+' m → '+(lenOk?'PASS':'FAIL — exceeds 7.5 m combined limit')+'\nMechanical protection: '+(mech?'PASS':'FAIL')+'\nSecondary terminating OCPD: '+fmt(down)+' A → '+(downOk?'PASS':'FAIL — exceeds selected secondary ampacity')+'\n\nOVERALL: '+(pass?'PASS':'CHECK / FAIL')+'\n\nCEC 26-256 normally uses 125% transformer conductor sizing; where its protection arrangement sends the conductors to Rules 14-100/14-104, verify the exact demand/protection arrangement before using this tap exception.';
        })}

        function calcMotorTap(){run('tap_motor_out',function(){
          var mode=value('tap_motor_mode'), base=num('tap_motor_base'), selected=num('tap_motor_selected'), len=num('tap_motor_len'), flcs=csv('tap_motor_flcs');
          var load=motorLoad(flcs), required, route, conditions=true;
          if(mode==='GROUP_BRANCH'){
            var single=flcs[0]*1.25;
            if(len<=7.5){required=Math.max(single,base/3);route='Rule 28-106(3): individual motor tap ≤7.5 m';}
            else{required=base;route='Tap exceeds 7.5 m: use branch-circuit conductor ampacity';}
          }else{
            if(len<=3 && checked('tap_motor_metal')){required=load.required;route='Rule 28-110(2)(a): ≤3 m and enclosed in metal';}
            else if(len<=7.5 && checked('tap_motor_mech')){required=Math.max(load.required,base/3);route='Rule 28-110(2)(b): ≤7.5 m, ≥1/3 feeder, mechanically protected';}
            else{required=base;route='Reduced feeder-tap exception unavailable: tap ampacity must equal feeder';}
          }
          var pass=selected>=required;
          return 'MOTOR TAP — '+route+'\n\nEntered FLC(s): '+flcs.map(fmt).join(', ')+' A\nLargest motor FLC: '+fmt(load.largest)+' A\nContinuous-duty motor load basis (125% largest + others): '+fmt(load.required)+' A\nSource '+(mode==='GROUP_BRANCH'?'branch-circuit':'feeder')+' conductor ampacity: '+fmt(base)+' A\nOne-third value: '+fmt(base/3)+' A\n\nRequired tap ampacity: '+fmt(required)+' A\nStarter conductor: '+starter(required)+'\nSelected tap ampacity: '+fmt(selected)+' A → '+(pass?'PASS':'FAIL')+'\nTap length: '+fmt(len)+' m\n\nOVERALL: '+(pass?'PASS':'CHECK / FAIL')+'\n\nFor non-continuous-duty motors, replace the 125% basis with the applicable Table 27 calculation. Branch OCPD selection remains a separate Section 28 calculation.';
        })}

        function calcWelderTap(){run('tap_welder_out',function(){
          var type=value('tap_w_type'), rated=num('tap_w_rated'), actual=num('tap_w_actual'), duty=num('tap_w_duty'), feeder=num('tap_w_feeder'), selected=num('tap_w_selected'), len=num('tap_w_len'), down=num('tap_w_down');
          var load, label, sectionFactor=null;
          if(type==='TRANSFORMER'){load=rated*duty;label='Transformer / inverter: rated primary current × entered Table 42A factor';sectionFactor=2;}
          else if(type==='MOTOR_GENERATOR'){load=rated*duty;label='Motor-generator: rated primary current × entered Table 42B factor; Section 28 also applies';}
          else if(type==='RES_MANUAL'){load=rated*.50;label='Manual non-automatic resistance welder: 50% of rated primary current';sectionFactor=3;}
          else if(type==='RES_AUTO'){load=rated*.70;label='Seam / automatically fed resistance welder: 70% of rated primary current';sectionFactor=3;}
          else{load=actual*duty;label='Known actual primary current × entered Table 42C factor';sectionFactor=3;}
          var reduced=len<=7.5 && checked('tap_w_mech'), required=reduced?Math.max(load,feeder/3):feeder;
          var tapOk=selected>=required, downTapOk=!reduced || down<=selected;
          var sectionMax=sectionFactor?Math.min(rated*sectionFactor,selected*sectionFactor):null;
          var sectionOk=sectionMax===null || down<=sectionMax;
          var pass=tapOk&&downTapOk&&sectionOk;
          var extra=sectionMax===null?'Motor-generator welder: use the Motor Tap worksheet for the Section 28 tap conditions.':'Section 42 worksheet ceiling before any permitted next-standard-size exception: '+fmt(sectionMax)+' A → '+(sectionOk?'PASS':'CHECK');
          return 'WELDER TAP / SUPPLY CONDUCTOR\n\n'+label+'\nWelder supply-conductor load: '+fmt(load)+' A\nFeeder conductor ampacity: '+fmt(feeder)+' A\nOne-third feeder value: '+fmt(feeder/3)+' A\n\nReduced general tap available: '+(reduced?'YES — Rule 14-100(1)(c) conditions entered':'NO — full feeder ampacity shown')+'\nRequired tap ampacity: '+fmt(required)+' A\nStarter conductor: '+starter(required)+'\nSelected tap ampacity: '+fmt(selected)+' A → '+(tapOk?'PASS':'FAIL')+'\nDownstream OCPD: '+fmt(down)+' A'+(reduced?' → '+(downTapOk?'PASS':'FAIL — Rule 14-100(1)(c) requires the terminating OCPD not to exceed tap ampacity'):'')+'\n'+extra+'\n\nOVERALL: '+(pass?'PASS':'CHECK / FAIL')+'\n\nSection 42 does not create a separate reduced-size tap rule. First calculate the welder supply conductor under Section 42, then satisfy the applicable general tap rule. Motor-generator welders also invoke Section 28.';
        })}

        function tapPane(title,body,outId,buttonText,handler){
          return '<div class="tap-pane"><div class="workspace"><div><div class="card"><h3>'+title+'</h3>'+body+'</div><button class="btn" type="button" data-handler="'+handler+'">'+buttonText+'</button></div><pre class="result" id="'+outId+'">Enter the question facts, then calculate.</pre></div></div>';
        }
        function addTapCalculators(){
          var section=document.getElementById('s14'); if(!section || section.querySelector('.tap-advanced')) return;
          var oldTabs=section.querySelector('.tabs'); if(oldTabs) oldTabs.style.display='none';
          section.querySelectorAll('.pane').forEach(function(p){p.style.display='none'});
          var lead=section.querySelector('.lead'); if(lead) lead.textContent='CEC 2024 tap-conductor worksheets: general taps, transformer primary/secondary taps, motor taps and welder supply taps.';
          var wrap=document.createElement('div');wrap.className='tap-advanced';
          var general='<div class="grid"><div class="field wide"><label>Tap rule mode</label><select id="tap_general_mode"><option value="SEVEN_FIVE">7.5 m one-third tap — Rule 14-100(1)(c)</option><option value="THREE_METRE">3 m equipment tap — Rule 14-100(1)(b)</option></select></div><div class="field"><label>Larger feeder conductor ampacity A</label><input id="tap_general_feeder" type="number" value="285"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_general_selected" type="number" value="100"></div><div class="field"><label>Tap length m</label><input id="tap_general_len" type="number" step="0.1" value="7.5"></div><div class="field"><label>Downstream single OCPD A (7.5 m mode)</label><input id="tap_general_down" type="number" value="100"></div><div class="field"><label>Computed load A (3 m mode)</label><input id="tap_general_load" type="number" value="80"></div><div class="field"><label>Supplied equipment amp rating A (3 m mode)</label><input id="tap_general_equipment" type="number" value="100"></div></div><div class="tap-checks"><label><input id="tap_general_mech" type="checkbox" checked>Mechanically protected for the 7.5 m one-third tap.</label><label><input id="tap_general_enclosed" type="checkbox" checked>3 m tap is enclosed in the required raceway/cable method.</label><label><input id="tap_general_noextend" type="checkbox" checked>3 m tap does not extend beyond the equipment it supplies.</label></div><div class="tap-callout">Use the ampacity of the larger conductor for the one-third test. The 3 m exception has different conditions and is not automatically a one-third calculation.</div>';
          var tx='<div class="grid"><div class="field"><label>Larger feeder conductor ampacity A</label><input id="tap_tx_feeder" type="number" value="300"></div><div class="field"><label>Transformer kVA</label><input id="tap_tx_kva" type="number" value="75"></div><div class="field"><label>Primary voltage V</label><input id="tap_tx_pv" type="number" value="600"></div><div class="field"><label>Secondary voltage V</label><input id="tap_tx_sv" type="number" value="208"></div><div class="field"><label>Phases</label><select id="tap_tx_ph"><option value="3">3</option><option value="1">1</option></select></div><div class="field"><label>Selected primary tap ampacity A</label><input id="tap_tx_pa" type="number" value="100"></div><div class="field"><label>Selected secondary conductor ampacity A</label><input id="tap_tx_sa" type="number" value="300"></div><div class="field"><label>Primary tap length m</label><input id="tap_tx_plen" type="number" step="0.1" value="3"></div><div class="field"><label>Secondary conductor length m</label><input id="tap_tx_slen" type="number" step="0.1" value="4"></div><div class="field"><label>Secondary terminating OCPD A</label><input id="tap_tx_down" type="number" value="300"></div></div><div class="tap-checks"><label><input id="tap_tx_mech" type="checkbox" checked>Primary and secondary tap conductors are protected from mechanical damage.</label></div><div class="tap-callout">Rule 14-100(1)(f) uses a one-third primary-conductor floor, the transformer voltage ratio for the secondary conductor, a combined 7.5 m primary + secondary length limit, mechanical protection, and a single terminating secondary OCPD. Verify the transformer protection arrangement under Section 26 as well.</div>';
          var motor='<div class="grid"><div class="field wide"><label>Motor tap mode</label><select id="tap_motor_mode"><option value="FEEDER">Feeder tap to one motor branch OCPD — Rule 28-110(2)</option><option value="GROUP_BRANCH">Group branch circuit tap to individual motor — Rule 28-106(3)</option></select></div><div class="field"><label>Feeder / branch conductor ampacity A</label><input id="tap_motor_base" type="number" value="200"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_motor_selected" type="number" value="85"></div><div class="field wide"><label>Motor FLC(s) A, comma-separated</label><input id="tap_motor_flcs" value="40"></div><div class="field"><label>Tap length m</label><input id="tap_motor_len" type="number" step="0.1" value="7.5"></div></div><div class="tap-checks"><label><input id="tap_motor_metal" type="checkbox">Tap is ≤3 m and enclosed in metal (28-110(2)(a)).</label><label><input id="tap_motor_mech" type="checkbox" checked>Tap is suitably protected from mechanical damage (28-110(2)(b)).</label></div><div class="tap-callout">For continuous-duty motors the worksheet uses 125% of the largest FLC plus the remaining FLCs. For non-continuous duty, use the applicable Table 27 percentage instead.</div>';
          var welder='<div class="grid"><div class="field wide"><label>Welder type</label><select id="tap_w_type"><option value="TRANSFORMER">Transformer / inverter — Table 42A factor</option><option value="MOTOR_GENERATOR">Motor-generator — Table 42B factor + Section 28</option><option value="RES_MANUAL">Resistance, manual non-automatic — 50%</option><option value="RES_AUTO">Resistance, seam / automatic — 70%</option><option value="RES_KNOWN">Resistance, known actual current — Table 42C factor</option></select></div><div class="field"><label>Rated primary current A</label><input id="tap_w_rated" type="number" value="100"></div><div class="field"><label>Actual primary current A (known-current mode)</label><input id="tap_w_actual" type="number" value="100"></div><div class="field"><label>Duty-cycle / table factor</label><input id="tap_w_duty" type="number" step="0.01" value="0.63"></div><div class="field"><label>Feeder conductor ampacity A</label><input id="tap_w_feeder" type="number" value="200"></div><div class="field"><label>Selected tap conductor ampacity A</label><input id="tap_w_selected" type="number" value="85"></div><div class="field"><label>Tap length m</label><input id="tap_w_len" type="number" step="0.1" value="7.5"></div><div class="field"><label>Downstream welder OCPD A</label><input id="tap_w_down" type="number" value="80"></div></div><div class="tap-checks"><label><input id="tap_w_mech" type="checkbox" checked>Tap is suitably protected from mechanical damage for Rule 14-100(1)(c).</label></div><div class="tap-callout">There is no separate Section 42 reduced-tap allowance. This combines the Section 42 welder supply-conductor load with the general Section 14 tap condition. Motor-generator welders are also subject to Section 28.</div>';
          wrap.innerHTML='<div class="tap-tabs"><button type="button" class="active">General Tap</button><button type="button">Transformer Tap</button><button type="button">Motor Tap</button><button type="button">Welder Tap</button></div>'+
            tapPane('General feeder / splitter tap',general,'tap_general_out','Calculate general tap','general')+
            tapPane('Transformer primary + secondary tap',tx,'tap_transformer_out','Calculate transformer tap','transformer')+
            tapPane('Motor tap conductor',motor,'tap_motor_out','Calculate motor tap','motor')+
            tapPane('Welder supply tap',welder,'tap_welder_out','Calculate welder tap','welder');
          section.appendChild(wrap);
          var panes=wrap.querySelectorAll('.tap-pane'), tabs=wrap.querySelectorAll('.tap-tabs button');
          panes[0].classList.add('active');
          tabs.forEach(function(btn,index){btn.addEventListener('click',function(){tabs.forEach(function(x){x.classList.remove('active')});panes.forEach(function(x){x.classList.remove('active')});btn.classList.add('active');panes[index].classList.add('active')})});
          wrap.querySelector('[data-handler="general"]').addEventListener('click',calcGeneralTap);
          wrap.querySelector('[data-handler="transformer"]').addEventListener('click',calcTransformerTap);
          wrap.querySelector('[data-handler="motor"]').addEventListener('click',calcMotorTap);
          wrap.querySelector('[data-handler="welder"]').addEventListener('click',calcWelderTap);

          var wLead=document.querySelector('#s42 .lead');
          if(wLead && !document.querySelector('#s42 .tap-link')){
            var link=document.createElement('a');link.href='#';link.className='tap-link';link.textContent='Open Section 14 tap-conductor calculators →';
            link.addEventListener('click',function(ev){ev.preventDefault();var b=[].slice.call(document.querySelectorAll('.nav button')).find(function(x){return x.textContent.indexOf('Section 14')>=0});if(b)b.click()});
            wLead.parentElement.insertBefore(link,wLead.nextSibling);
          }
        }

        function initEnhancements(){addVisualBendEntry();addTapCalculators()}
        if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',function(){setTimeout(initEnhancements,0)});
        else setTimeout(initEnhancements,0);
        setTimeout(initEnhancements,250);
      })();
      </script>
      """;

    return html.replace("</body>", enhancement + "</body>");
  }
}
