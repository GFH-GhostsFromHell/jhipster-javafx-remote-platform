from pathlib import Path

java_path = Path('desktop-client/src/main/java/com/example/desktop/GuidedWorksheetPane.java')
web_path = Path('portal/src/main/resources/static/guided-calculators.html')

java = java_path.read_text()

old_tabs = 'tabs.getTabs().addAll(tab("Quick Start",quickStart()),tab("Conductor + VD",conductor()),tab("Table D3 / K",d3()),tab("Ground / Bond",groundBond()),tab("Transformer",transformer()),tab("Motor / Tap",motorTap()),tab("Refrigeration",refrigeration()),tab("Fire Pump",firePump()),tab("Neutral",neutral()),tab("Fire Alarm",fireAlarm()),tab("Service Demand",serviceDemand()));'
new_tabs = 'tabs.getTabs().addAll(tab("Quick Start",quickStart()),tab("Conductor Ampacity",conductorAmpacity()),tab("Conductor + VD",conductor()),tab("Table D3 / K",d3()),tab("Ground / Bond",groundBond()),tab("Transformer",transformer()),tab("Motor / Tap",motorTap()),tab("Refrigeration",refrigeration()),tab("Fire Pump",firePump()),tab("Neutral",neutral()),tab("Fire Alarm",fireAlarm()),tab("Service Demand",serviceDemand()));'
if old_tabs not in java:
    raise SystemExit('Java tab marker not found')
java = java.replace(old_tabs, new_tabs, 1)

old_shortcuts = '''            shortcut("40 A · 120 V voltage drop",1,()->{}),
            shortcut("Table D3 / conductor length",2,()->{}),
            shortcut("Transformer · 75 kVA · 600/208 V",4,()->{}),
            shortcut("42 A motor · 2 m splitter tap",5,()->{if(tapLengthChoice!=null)tapLengthChoice.setValue("2 m");}),
            shortcut("42 A motor · 5 m splitter tap",5,()->{if(tapLengthChoice!=null)tapLengthChoice.setValue("5 m");}),
            shortcut("Compressor fails to start",6,()->{if(firstCompressorFails!=null)firstCompressorFails.setSelected(true);}),
            shortcut("Fire pump feeder",7,()->{}),
            shortcut("347/600 V vector neutral",8,()->{}),
            shortcut("24 V / 3.9 kΩ fire alarm EOL",9,()->{}),
            shortcut("Hospital service",10,()->{if(serviceOccupancy!=null)serviceOccupancy.setValue("Hospital");}),
            shortcut("Apartment service",10,()->{if(serviceOccupancy!=null)serviceOccupancy.setValue("Apartment / multi-suite");})'''
new_shortcuts = '''            shortcut("Conductor ampacity · derating · branch limits",1,()->{}),
            shortcut("40 A · 120 V voltage drop",2,()->{}),
            shortcut("Table D3 / conductor length",3,()->{}),
            shortcut("Transformer · 75 kVA · 600/208 V",5,()->{}),
            shortcut("42 A motor · 2 m splitter tap",6,()->{if(tapLengthChoice!=null)tapLengthChoice.setValue("2 m");}),
            shortcut("42 A motor · 5 m splitter tap",6,()->{if(tapLengthChoice!=null)tapLengthChoice.setValue("5 m");}),
            shortcut("Compressor fails to start",7,()->{if(firstCompressorFails!=null)firstCompressorFails.setSelected(true);}),
            shortcut("Fire pump feeder",8,()->{}),
            shortcut("347/600 V vector neutral",9,()->{}),
            shortcut("24 V / 3.9 kΩ fire alarm EOL",10,()->{}),
            shortcut("Hospital service",11,()->{if(serviceOccupancy!=null)serviceOccupancy.setValue("Hospital");}),
            shortcut("Apartment service",11,()->{if(serviceOccupancy!=null)serviceOccupancy.setValue("Apartment / multi-suite");})'''
if old_shortcuts not in java:
    raise SystemExit('Java shortcut marker not found')
java = java.replace(old_shortcuts, new_shortcuts, 1)

java_method = r'''
    private Node conductorAmpacity(){
        VBox f=new VBox(9);
        TextField load=field("16"),ocpd=field("20"),ambientTemp=field("30"),ambient=field("1.00"),ccc=field("2"),grouping=field("1.00"),manualBase=field(""),manualTerminal=field("");
        ChoiceBox<String> circuit=choice("General / feeder","General / feeder","Dwelling-unit lighting","General-use receptacle — 15 A","General-use receptacle — 20 A","Dwelling dryer receptacle — 30 A","Motor / HVAC / nameplate special rule");
        ChoiceBox<String> material=choice("Copper","Copper","Aluminum"),terminal=choice("75 °C","60 °C","75 °C","90 °C"),loading=choice("Non-continuous / 100% load","Non-continuous / 100% load","Continuous on 80%-rated equipment — 125%","Continuous on 100%-rated equipment — 100%");
        f.getChildren().addAll(row("Load current (A)",load),row("Circuit / use",circuit),row("Selected OCPD / breaker (A)",ocpd),row("Load treatment",loading),row("Conductor material",material),row("Equipment terminal temperature",terminal));
        advancedOnly(ambientTemp);advancedOnly(ambient);advancedOnly(ccc);advancedOnly(grouping);advancedOnly(manualBase);advancedOnly(manualTerminal);
        f.getChildren().addAll(advancedRow("Ambient temperature °C (reference only)",ambientTemp),advancedRow("Ambient correction factor — Table 5A input",ambient),advancedRow("Current-carrying conductors",ccc),advancedRow("Grouping factor — Table 5C input",grouping),advancedRow("Selected conductor insulation-column ampacity A (optional exact verification)",manualBase),advancedRow("Terminal-temperature ampacity limit A (optional exact verification)",manualTerminal));
        return worksheet("Conductor ampacity / derating / branch-circuit limits","Sizes a conductor from load current and correction factors, then separately checks the branch-circuit/OCPD restrictions that can govern even when the raw conductor ampacity is higher. Automatic size selection uses the same built-in 75 °C starter dataset as the rest of Electrical Workbench; use the two optional exact-verification fields when a 60 °C or 90 °C table column controls.",f,()->{
            double i=num(load),oc=num(ocpd),af=num(ambient),gf=num(grouping);if(i<0||oc<=0||af<=0||gf<=0)throw new IllegalArgumentException("Load, breaker and correction factors must be valid positive values");
            double loadFactor=loading.getValue().startsWith("Continuous on 80")?1.25:1.0;
            double required=i*loadFactor,requiredTable=required/(af*gf);
            LinkedHashMap<String,Double> map=material.getValue().equals("Aluminum")?AL:CU;
            String ampacitySize=findAmpacity(map,requiredTable);if(ampacitySize==null)return "Required corrected ampacity exceeds the starter conductor dataset.";
            List<String> sizes=new ArrayList<>(map.keySet());int idx=sizes.indexOf(ampacitySize);String finalSize=ampacitySize;
            boolean special=circuit.getValue().startsWith("Motor / HVAC");double circuitCap=Double.POSITIVE_INFINITY;String circuitMinimum=null;String circuitRule="No additional branch-circuit preset selected; apply the normal conductor/OCPD rules.";
            if(circuit.getValue().equals("Dwelling-unit lighting")){circuitCap=15;circuitMinimum="14 AWG";circuitRule="Dwelling lighting study path: 15 A branch-circuit restriction discussed under Rule 30-104.";}
            else if(circuit.getValue().equals("General-use receptacle — 15 A")){circuitCap=15;circuitMinimum="14 AWG";circuitRule="15 A general-use receptacle preset.";}
            else if(circuit.getValue().equals("General-use receptacle — 20 A")){circuitCap=20;circuitMinimum="12 AWG";circuitRule="20 A general-use/T-slot receptacle preset; #12 Cu is the minimum normal small-conductor path.";}
            else if(circuit.getValue().equals("Dwelling dryer receptacle — 30 A")){circuitCap=30;circuitMinimum="10 AWG";circuitRule="Dwelling dryer preset: 30 A branch-circuit/receptacle path previously discussed under Rule 26-744.";}
            else if(special){circuitRule="Motor/HVAC/nameplate path selected: special Section 28/62 or marked MCA/MOCP rules can permit conductor/OCPD relationships that differ from ordinary Rule 14-104 small-conductor circuits.";}
            if(material.getValue().equals("Copper")&&!special){
                if(circuitMinimum!=null){int min=sizes.indexOf(circuitMinimum);if(min>=0&&min>idx){idx=min;finalSize=sizes.get(idx);}}
                while(idx<sizes.size()-1){double cap=finalSize.equals("14 AWG")?15:finalSize.equals("12 AWG")?20:finalSize.equals("10 AWG")?30:Double.POSITIVE_INFINITY;if(oc<=cap)break;finalSize=sizes.get(++idx);}
            }
            double corrected=map.get(finalSize)*af*gf;
            String circuitStatus=Double.isFinite(circuitCap)&&oc>circuitCap?String.format(Locale.US,"REVIEW — selected %.0f A OCPD exceeds this preset's %.0f A branch-circuit limit; increasing conductor size does not remove that circuit restriction.",oc,circuitCap):"PASS / no preset OCPD conflict detected.";
            String smallRule;
            if(material.getValue().equals("Copper")&&!special)smallRule="Rule 14-104(2) small-copper check: #14 Cu → 15 A max normal OCPD; #12 Cu → 20 A; #10 Cu → 30 A. Final size after this check = "+finalSize+".";
            else if(material.getValue().equals("Aluminum")&&!special)smallRule="Aluminum selected: automatic small-conductor OCPD limits are not embedded because the exact aluminum limits were not verified in the source material; verify Rule 14-104 before field use.";
            else smallRule="Special equipment path selected; verify the applicable motor/HVAC/nameplate rule instead of applying the ordinary small-conductor cap blindly.";
            String manual="Not entered.";
            if(!manualBase.getText().trim().isEmpty()){
                double base=Double.parseDouble(manualBase.getText().trim()),allow=base*af*gf;String termText="";
                if(!manualTerminal.getText().trim().isEmpty()){double tl=Double.parseDouble(manualTerminal.getText().trim());allow=Math.min(allow,tl);termText=String.format(Locale.US," and terminal limit %.2f A",tl);}
                manual=String.format(Locale.US,"Exact verification: %.2f A table value × %.3f × %.3f%s → allowable %.2f A → %s required %.2f A.",base,af,gf,termText,allow,allow>=required?"PASS":"DOES NOT PASS",required);
            }
            return lines(
                "GIVEN",String.format(Locale.US,"Load %.2f A · OCPD %.0f A · %s · %s · terminal %s",i,oc,circuit.getValue(),material.getValue(),terminal.getValue()),
                "1 — LOAD AMPACITY",String.format(Locale.US,"Required load ampacity = %.2f × %.3f = %.2f A",i,loadFactor,required),
                "2 — DERATING",String.format(Locale.US,"Required uncorrected table ampacity = %.2f /(ambient %.3f × grouping %.3f) = %.2f A. Current-carrying conductors entered: %.0f; ambient reference: %.1f °C.",required,af,gf,requiredTable,num(ccc),num(ambientTemp)),
                "3 — STARTER SIZE","75 °C starter lookup gives "+ampacitySize+" ("+fmt(map.get(ampacitySize))+" A). After branch/OCPD restrictions: "+finalSize+". Corrected starter ampacity = "+fmt(corrected)+" A.",
                "4 — SMALL-CONDUCTOR / CIRCUIT CHECK",smallRule+"\n"+circuitRule+"\n"+circuitStatus,
                "5 — EXACT TABLE VERIFICATION",manual,
                "FINAL","Study selection: "+finalSize+" or larger, provided the applicable CEC/OESC table column, terminal rating, correction factors, wiring method and special circuit rule all pass.",
                "WHY","Raw table ampacity, derated allowable ampacity, terminal-temperature limitation and branch-circuit/OCPD restrictions are separate checks. A higher insulation ampacity does not by itself authorize a larger breaker on an ordinary small-conductor circuit."
            );
        },"conductor-ampacity");
    }

'''
marker = '    private Node conductor(){\n'
if marker not in java:
    raise SystemExit('Java conductor method marker not found')
java = java.replace(marker, java_method + marker, 1)
java_path.write_text(java)

web = web_path.read_text()
web_section = r'''<section id="ampacity" class="module"><h2>Conductor Ampacity / Sizing</h2><p class="lead">Load ampacity, ambient/grouping derating, terminal limitation and branch-circuit/OCPD restrictions as separate checks.</p><div class="card"><div class="fields"><F id="a_i" l="Load current A" v="16"></F><S id="a_use" l="Circuit / use" o="General / feeder|Dwelling-unit lighting|General-use receptacle — 15 A|General-use receptacle — 20 A|Dwelling dryer receptacle — 30 A|Motor / HVAC / nameplate special rule"></S><F id="a_oc" l="Selected OCPD / breaker A" v="20"></F><S id="a_load" l="Load treatment" o="Non-continuous / 100% load|Continuous on 80%-rated equipment — 125%|Continuous on 100%-rated equipment — 100%"></S><S id="a_mat" l="Material" o="Copper|Aluminum"></S><S id="a_term" l="Terminal temperature" o="75 °C|60 °C|90 °C"></S><F class="adv" id="a_temp" l="Ambient °C (reference only)" v="30"></F><F class="adv" id="a_amb" l="Ambient correction factor — Table 5A input" v="1.00"></F><F class="adv" id="a_ccc" l="Current-carrying conductors" v="2"></F><F class="adv" id="a_grp" l="Grouping factor — Table 5C input" v="1.00"></F><F class="adv" id="a_base" l="Selected conductor insulation-column ampacity A (optional exact verification)" v=""></F><F class="adv" id="a_tlim" l="Terminal-temperature ampacity limit A (optional exact verification)" v=""></F></div><button class="btn" onclick="ampacity()">Size conductor & explain</button><O id="o_ampacity"></O><p class="note">Automatic sizing uses the existing Electrical Workbench 75 °C starter dataset. Enter the exact insulation-column and terminal-limit ampacities in Advanced mode when a 60 °C or 90 °C table column controls.</p></div></section>
'''
web_marker = '<section id="conductor" class="module">'
if web_marker not in web:
    raise SystemExit('Web conductor section marker not found')
web = web.replace(web_marker, web_section + web_marker, 1)

old_mods = "const mods=[['quick','Quick Start'],['conductor','Conductor + VD'],['d3','Table D3 / K'],['ground','Ground / Bond'],['xfmr','Transformer'],['motor','Motor / Tap'],['refrig','Refrigeration'],['firepump','Fire Pump'],['neutral','Neutral'],['firealarm','Fire Alarm'],['service','Service Demand']];"
new_mods = "const mods=[['quick','Quick Start'],['ampacity','Conductor Ampacity'],['conductor','Conductor + VD'],['d3','Table D3 / K'],['ground','Ground / Bond'],['xfmr','Transformer'],['motor','Motor / Tap'],['refrig','Refrigeration'],['firepump','Fire Pump'],['neutral','Neutral'],['firealarm','Fire Alarm'],['service','Service Demand']];"
if old_mods not in web:
    raise SystemExit('Web mods marker not found')
web = web.replace(old_mods,new_mods,1)

old_presets = "const presets=[['40 A · 120 V voltage drop','conductor'],['Table D3 / conductor length','d3'],['75 kVA · 600/208 V transformer','xfmr'],['42 A motor · 2 m tap','motor'],['42 A motor · 5 m tap','motor'],['Compressor fails to start','refrig'],['Fire pump feeder','firepump'],['347/600 V vector neutral','neutral'],['24 V / 3.9 kΩ EOL','firealarm'],['Hospital service','service'],['Apartment service','service']];"
new_presets = "const presets=[['Conductor ampacity · derating · branch limits','ampacity'],['40 A · 120 V voltage drop','conductor'],['Table D3 / conductor length','d3'],['75 kVA · 600/208 V transformer','xfmr'],['42 A motor · 2 m tap','motor'],['42 A motor · 5 m tap','motor'],['Compressor fails to start','refrig'],['Fire pump feeder','firepump'],['347/600 V vector neutral','neutral'],['24 V / 3.9 kΩ EOL','firealarm'],['Hospital service','service'],['Apartment service','service']];"
if old_presets not in web:
    raise SystemExit('Web presets marker not found')
web = web.replace(old_presets,new_presets,1)

old_preset_fn = "function preset(i,id){if(i===4)val('m_len','5 m');if(i===3)val('m_len','2 m');if(i===5)document.getElementById('r_fail1').checked=true;if(i===9)val('s_occ','Hospital');if(i===10)val('s_occ','Apartment / multi-suite');"
new_preset_fn = "function preset(i,id){if(i===5)val('m_len','5 m');if(i===4)val('m_len','2 m');if(i===6)document.getElementById('r_fail1').checked=true;if(i===10)val('s_occ','Hospital');if(i===11)val('s_occ','Apartment / multi-suite');"
if old_preset_fn not in web:
    raise SystemExit('Web preset function marker not found')
web = web.replace(old_preset_fn,new_preset_fn,1)

web_function = r'''function ampacity(){out('o_ampacity',()=>{const I=n('a_i'),oc=n('a_oc'),af=n('a_amb'),gf=n('a_grp'),mat=s('a_mat'),use=s('a_use'),special=use.startsWith('Motor / HVAC'),map=mat==='Copper'?CU:AL;if(I<0||oc<=0||af<=0||gf<=0)throw Error('Load, breaker and correction factors must be valid positive values');const lf=s('a_load').startsWith('Continuous on 80')?1.25:1,req=I*lf,need=req/(af*gf);let ampSize=find(map,need);if(!ampSize)return 'Required corrected ampacity exceeds the starter conductor dataset.';const keys=Object.keys(map);let idx=keys.indexOf(ampSize),size=ampSize,cap=Infinity,minSize=null,rule='No additional branch-circuit preset selected; apply the normal conductor/OCPD rules.';if(use==='Dwelling-unit lighting'){cap=15;minSize='14 AWG';rule='Dwelling lighting study path: 15 A branch-circuit restriction discussed under Rule 30-104.'}else if(use==='General-use receptacle — 15 A'){cap=15;minSize='14 AWG';rule='15 A general-use receptacle preset.'}else if(use==='General-use receptacle — 20 A'){cap=20;minSize='12 AWG';rule='20 A general-use/T-slot receptacle preset; #12 Cu is the minimum normal small-conductor path.'}else if(use==='Dwelling dryer receptacle — 30 A'){cap=30;minSize='10 AWG';rule='Dwelling dryer preset: 30 A branch-circuit/receptacle path previously discussed under Rule 26-744.'}else if(special){rule='Motor/HVAC/nameplate path: verify the applicable special section and marked MCA/MOCP; ordinary small-conductor breaker limits are not applied blindly.'}if(mat==='Copper'&&!special){if(minSize&&keys.indexOf(minSize)>idx){idx=keys.indexOf(minSize);size=keys[idx]}while(idx<keys.length-1){const sc=size==='14 AWG'?15:size==='12 AWG'?20:size==='10 AWG'?30:Infinity;if(oc<=sc)break;size=keys[++idx]}}const corrected=map[size]*af*gf,circuitStatus=Number.isFinite(cap)&&oc>cap?`REVIEW — selected ${oc.toFixed(0)} A OCPD exceeds this preset's ${cap.toFixed(0)} A circuit limit; a larger conductor does not remove that circuit restriction.`:'PASS / no preset OCPD conflict detected.';let small=mat==='Copper'&&!special?`Rule 14-104(2) small-copper check: #14 Cu → 15 A, #12 Cu → 20 A, #10 Cu → 30 A maximum normal OCPD. Final size after this check = ${size}.`:mat==='Aluminum'&&!special?'Aluminum selected: exact small-conductor OCPD limits are not embedded because they were not verified in the source material; verify Rule 14-104.':'Special equipment path selected; use the applicable motor/HVAC/nameplate rule.';let manual='Not entered.';const b=document.getElementById('a_base').value.trim(),tl=document.getElementById('a_tlim').value.trim();if(b){const base=parseFloat(b);if(!Number.isFinite(base))throw Error('Check exact table ampacity');let allow=base*af*gf,term='';if(tl){const lim=parseFloat(tl);if(!Number.isFinite(lim))throw Error('Check terminal ampacity limit');allow=Math.min(allow,lim);term=` and terminal limit ${fmt(lim)} A`}manual=`Exact verification: ${fmt(base)} A × ${fmt(af,3)} × ${fmt(gf,3)}${term} → allowable ${fmt(allow)} A → ${allow>=req?'PASS':'DOES NOT PASS'} required ${fmt(req)} A.`}return block('GIVEN',`${fmt(I)} A load · ${fmt(oc,0)} A OCPD · ${use} · ${mat} · ${s('a_term')} terminals`,'1 — LOAD AMPACITY',`Required load ampacity = ${fmt(I)} × ${fmt(lf,3)} = ${fmt(req)} A`,'2 — DERATING',`Required uncorrected table ampacity = ${fmt(req)} /(ambient ${fmt(af,3)} × grouping ${fmt(gf,3)}) = ${fmt(need)} A. Current-carrying conductors entered: ${fmt(n('a_ccc'),0)}; ambient reference: ${fmt(n('a_temp'),1)} °C.`,'3 — STARTER SIZE',`75 °C starter lookup = ${ampSize} (${fmt(map[ampSize])} A). After branch/OCPD restrictions = ${size}. Corrected starter ampacity = ${fmt(corrected)} A.`,'4 — SMALL-CONDUCTOR / CIRCUIT CHECK',`${small}\n${rule}\n${circuitStatus}`,'5 — EXACT TABLE VERIFICATION',manual,'FINAL',`Study selection: ${size} or larger, provided the applicable CEC/OESC table column, terminal rating, correction factors, wiring method and special circuit rule all pass.`,'WHY','Raw table ampacity, derated ampacity, terminal-temperature limitations and branch-circuit/OCPD restrictions are separate checks. Higher insulation ampacity does not by itself authorize a larger breaker on an ordinary small-conductor circuit.')} )}
'''
web_func_marker = 'function conductor(){'
if web_func_marker not in web:
    raise SystemExit('Web conductor function marker not found')
web = web.replace(web_func_marker, web_function + web_func_marker, 1)
web_path.write_text(web)

print('Patched dedicated conductor ampacity calculator into JavaFX and shared web workspace.')
