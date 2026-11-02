package com.example.remoteportal.web;

import jakarta.servlet.http.HttpServletResponse;
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
  public String calculatorWorkspace(HttpServletResponse response) throws IOException {
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);

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
        function num(id){
          var el=document.getElementById(id),v=el?parseFloat(el.value):NaN;
          if(!Number.isFinite(v)) throw new Error('Check '+id);
          return v;
        }
        function val(id){var el=document.getElementById(id);return el?el.value:'';}
        function motorFactor(type,start,protection,flc){
          if(type.indexOf('Wound-rotor')===0) return 1.50;
          if(start.indexOf('Autotransformer')===0){
            if(protection==='Time-delay fuse') return 1.75;
            if(protection==='Non-time-delay fuse') return flc<=30?2.50:2.00;
            return 2.00;
          }
          if(protection==='Time-delay fuse') return 1.75;
          if(protection==='Non-time-delay fuse') return 3.00;
          return 2.50;
        }
        function standardNotAbove(amps){
          var sizes=[15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200];
          var out=sizes[0];
          for(var k=0;k<sizes.length;k++){if(sizes[k]>amps)break;out=sizes[k];}
          return out;
        }
        function starter(amps){
          var table=[[20,'#14'],[25,'#12'],[35,'#10'],[50,'#8'],[65,'#6'],[85,'#4'],[100,'#3'],[115,'#2'],[130,'#1'],[150,'1/0'],[175,'2/0'],[200,'3/0'],[230,'4/0']];
          for(var k=0;k<table.length;k++) if(amps<=table[k][0]) return table[k][1]+' Cu (starter 75°C lookup)';
          return 'Above starter lookup range';
        }
        function setResult(id,text){var el=document.getElementById(id);if(el)el.textContent=text;}
        function calculateAcMotor(){
          try{
            var type=val('ac_type'),ph=num('ac_ph'),flc=num('ac_flc'),start=val('ac_start'),protection=val('ac_prot');
            var cf=val('ac_duty')==='custom'?num('ac_cf_custom'):1.25;
            var overload=parseFloat(val('ac_ol')),disc=num('ac_df'),factor=motorFactor(type,start,protection,flc);
            if(flc<=0||cf<=0||overload<=0||disc<=0) throw new Error('Motor values must be greater than zero');
            var conductor=flc*cf,ol=flc*overload,ocpdCalc=flc*factor,ocpd=standardNotAbove(ocpdCalc),disconnect=flc*disc;
            setResult('o_acm',
              'AC MOTOR BRANCH WORKSHEET\n\n'+
              'Motor type: '+type+'\nPhases: '+ph+'\nStarting method: '+start+'\nProtection: '+protection+'\n\n'+
              '1) MOTOR CURRENT\nFLC / FLA = '+flc.toFixed(2)+' A\n\n'+
              '2) BRANCH CONDUCTOR\nFactor = '+(cf*100).toFixed(0)+'%\nRequired ampacity = '+conductor.toFixed(2)+' A\nStarter conductor = '+starter(conductor)+'\n\n'+
              '3) SHORT-CIRCUIT / GROUND-FAULT OCPD\nTable 29 worksheet factor = '+(factor*100).toFixed(0)+'%\nCalculated maximum = '+ocpdCalc.toFixed(2)+' A\nCommon standard size not exceeding = '+ocpd+' A\n\n'+
              '4) OVERLOAD\nFactor = '+(overload*100).toFixed(0)+'%\nMaximum worksheet setting = '+ol.toFixed(2)+' A\n\n'+
              '5) DISCONNECT\nFactor = '+(disc*100).toFixed(0)+'%\nMinimum current rating = '+disconnect.toFixed(2)+' A\n\n'+
              'References: CEC 2024 Section 28 and Table 29. Verify the applicable motor-current table, equipment markings, controller requirements and any permitted OCPD exceptions before field use.');
          }catch(err){setResult('o_acm','Check inputs: '+err.message);}
        }
        function calculateMotorTap(){
          try{
            var mode=val('mt_mode'),source=num('mt_source'),flc=num('mt_flc'),tap=num('mt_tap'),len=num('mt_len'),install=val('mt_install');
            var duty=val('mt_duty')==='custom'?num('mt_custom'):1.25;
            if(source<=0||flc<=0||tap<=0||len<0||duty<=0) throw new Error('Check motor tap inputs');
            var motorReq=flc*duty,third=source/3,required=0,condition='',valid=true,ref='';
            if(mode.indexOf('Feeder / splitter')===0){
              ref='CEC 2024 Rule 28-110(2)';
              if(install.indexOf('≤3 m')===0){
                valid=len<=3;required=motorReq;condition='≤3 m and enclosed in metal: use the motor branch-conductor calculation.';
              }else if(install.indexOf('≤7.5 m')===0){
                valid=len<=7.5;required=Math.max(motorReq,third);condition='≤7.5 m protected tap: motor calculation, but not less than one-third of feeder conductor ampacity.';
              }else{
                required=source;condition='Outside the short-tap exception: this worksheet requires at least the feeder conductor ampacity.';
              }
            }else{
              ref='CEC 2024 Rule 28-106(3)';
              if(len<=7.5){required=Math.max(motorReq,third);condition='Individual motor tap ≤7.5 m: motor calculation, but not less than one-third of the branch-circuit conductor ampacity.';}
              else{required=source;condition='Tap exceeds 7.5 m: this worksheet requires at least the branch-circuit conductor ampacity.';}
            }
            var pass=valid&&tap>=required;
            setResult('o_mt',
              'MOTOR TAP / SPLITTER WORKSHEET\n\n'+
              'Arrangement: '+mode+'\nTap length: '+len.toFixed(2)+' m\nInstallation: '+install+'\n\n'+
              'Motor conductor basis = '+flc.toFixed(2)+' A × '+(duty*100).toFixed(0)+'% = '+motorReq.toFixed(2)+' A\n'+
              'One-third source conductor ampacity = '+source.toFixed(2)+' ÷ 3 = '+third.toFixed(2)+' A\n\n'+
              condition+'\n\n'+
              'MINIMUM REQUIRED TAP AMPACITY = '+required.toFixed(2)+' A\n'+
              'Starter conductor = '+starter(required)+'\n'+
              'Selected tap ampacity = '+tap.toFixed(2)+' A\n'+
              'Length / installation condition = '+(valid?'PASS':'FAIL')+'\n'+
              'RESULT = '+(pass?'PASS':'FAIL')+'\n\n'+
              'Reference: '+ref+'. Verify mechanical protection, conductor derating/temperature limits, terminations and the downstream motor protection arrangement.');
          }catch(err){setResult('o_mt','Check inputs: '+err.message);}
        }
        function enhanceMotors(){
          var section=document.getElementById('s28');
          if(!section)return;
          var panes=section.querySelectorAll('[data-tabset="s28"]');
          if(panes.length<4)return;
          panes[0].innerHTML=`<div class="workspace"><div>
            <div class="card"><h3>AC motor branch worksheet</h3><div class="grid">
              <div class="field"><label>AC motor type</label><select id="ac_type"><option selected>Squirrel-cage induction</option><option>Synchronous</option><option>Wound-rotor induction</option><option>Single-phase split-phase</option><option>Capacitor-start</option><option>Capacitor-start / capacitor-run</option><option>Universal / other AC</option></select></div>
              <div class="field"><label>Phases</label><select id="ac_ph"><option value="3" selected>3</option><option value="1">1</option></select></div>
              <div class="field"><label>Motor FLC / FLA (A)</label><input id="ac_flc" type="number" value="34"></div>
              <div class="field"><label>Starting method</label><select id="ac_start"><option selected>Full voltage / resistor / reactor</option><option>Autotransformer / star-delta</option><option>Other / verify Table 29</option></select></div>
              <div class="field"><label>Branch protection</label><select id="ac_prot"><option selected>Time-delay fuse</option><option>Non-time-delay fuse</option><option>Inverse-time circuit breaker</option></select></div>
              <div class="field"><label>Motor duty</label><select id="ac_duty"><option value="standard" selected>Continuous duty — 125%</option><option value="custom">Custom / Table 27 factor</option></select></div>
              <div class="field"><label>Custom conductor factor</label><input id="ac_cf_custom" type="number" step="0.01" value="1.25"></div>
              <div class="field"><label>Overload condition</label><select id="ac_ol"><option value="1.25" selected>SF ≥ 1.15 or temp rise ≤ 40°C — 125%</option><option value="1.15">Other motor — 115%</option></select></div>
              <div class="field"><label>Disconnect factor</label><input id="ac_df" type="number" step="0.01" value="1.15"></div>
            </div><p class="note">Choose the motor and protective-device type. The worksheet selects the common Table 29 percentage automatically instead of making you remember the multiplier.</p></div>
            <button class="btn" id="ac_easy_calc">Calculate AC motor</button>
          </div><pre class="result" id="o_acm">Enter the motor facts, then calculate.</pre></div>`;
          var acButton=document.getElementById('ac_easy_calc');if(acButton)acButton.addEventListener('click',calculateAcMotor);

          if(!document.getElementById('motor_tap_tab')){
            var tabs=section.querySelector('.tabs');
            if(!tabs)return;
            var button=document.createElement('button');
            button.id='motor_tap_tab';button.type='button';button.textContent='Motor Taps / Splitter';
            tabs.appendChild(button);
            var pane=document.createElement('div');
            pane.className='pane';pane.setAttribute('data-tabset','s28');
            pane.innerHTML=`<div class="workspace"><div>
              <div class="card"><h3>Motor taps from splitters / motor feeders</h3><div class="grid">
                <div class="field wide"><label>Tap arrangement</label><select id="mt_mode"><option selected>Feeder / splitter → individual motor branch OCPD</option><option>One branch OCPD → individual motor tap</option></select></div>
                <div class="field"><label>Feeder / source conductor ampacity (A)</label><input id="mt_source" type="number" value="200"></div>
                <div class="field"><label>Motor FLC / FLA (A)</label><input id="mt_flc" type="number" value="40"></div>
                <div class="field"><label>Selected tap conductor ampacity (A)</label><input id="mt_tap" type="number" value="85"></div>
                <div class="field"><label>Tap length (m)</label><input id="mt_len" type="number" step="0.1" value="5"></div>
                <div class="field"><label>Tap installation</label><select id="mt_install"><option>≤3 m and enclosed in metal</option><option selected>≤7.5 m and mechanically protected</option><option>Longer / no short-tap exception</option></select></div>
                <div class="field"><label>Motor duty</label><select id="mt_duty"><option value="standard" selected>Continuous duty — 125%</option><option value="custom">Custom / Table 27 factor</option></select></div>
                <div class="field"><label>Custom duty factor</label><input id="mt_custom" type="number" step="0.01" value="1.25"></div>
              </div><p class="note">Checks motor taps from a feeder/splitter and individual motor taps on a group-motor branch circuit, including the 3 m and 7.5 m / one-third conditions.</p></div>
              <button class="btn" id="mt_calc">Calculate motor tap</button>
            </div><pre class="result" id="o_mt">Enter the motor tap facts, then calculate.</pre></div>`;
            section.appendChild(pane);
            button.addEventListener('click',function(){window.tab('s28',section.querySelectorAll('[data-tabset="s28"]').length-1,button);});
            var tapButton=document.getElementById('mt_calc');if(tapButton)tapButton.addEventListener('click',calculateMotorTap);
          }
          var lead=section.querySelector('.lead');if(lead)lead.textContent='AC motor dropdowns, DC motors, multiple motors, motor taps/splitters and refrigeration.';
        }
        function enhanceBoxFill(){
          var section=document.getElementById('s12');if(!section)return;
          var panes=section.querySelectorAll('[data-tabset="s12"]');if(panes.length<2)return;
          panes[1].innerHTML=`<div class="workspace"><div>
            <div class="card"><h3>Box fill</h3><div class="grid">
              <div class="field"><label>Box volume (mL)</label><input id="bf_vol" type="number" value="245"></div>
              <div class="field"><label>Conductor size / Table 22 volume</label><select id="bf_allow"><option value="24.6">14 AWG — 24.6 mL</option><option value="28.7">12 AWG — 28.7 mL</option><option value="36.9">10 AWG — 36.9 mL</option><option value="45.1">8 AWG — 45.1 mL</option><option value="73.7">6 AWG — 73.7 mL</option></select></div>
              <div class="field"><label>Insulated conductors</label><input id="bf_cond" type="number" value="8"></div>
              <div class="field"><label>Device equivalents</label><input id="bf_dev" type="number" value="2"></div>
              <div class="field"><label>Bond / clamp equivalents</label><input id="bf_bond" type="number" value="1"></div>
            </div><p class="note">Choose the conductor size; its Table 22 volume allowance is filled automatically.</p></div>
            <button class="btn" id="bf_easy_calc">Calculate box fill</button>
          </div><pre class="result" id="o_bf">Enter the box-fill facts, then calculate.</pre></div>`;
          var b=document.getElementById('bf_easy_calc');if(b)b.addEventListener('click',function(){window.post('/api/calculators/box-fill',{boxVolume:num('bf_vol'),conductorAllowance:num('bf_allow'),insulatedConductors:Math.trunc(num('bf_cond')),deviceEquivalents:Math.trunc(num('bf_dev')),bondClampEquivalents:Math.trunc(num('bf_bond'))},'o_bf');});
        }
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
        function applyEnhancements(){enhanceMotors();enhanceBoxFill();addVisualBendEntry();}
        if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',applyEnhancements);
        else applyEnhancements();
        setTimeout(applyEnhancements,250);
      })();
      </script>
      """;

    return html.replace("</body>", enhancement + "</body>");
  }
}
