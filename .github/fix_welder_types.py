from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

def sub_once(pattern, repl, text, label, flags=0):
    out, n = re.subn(pattern, repl, text, count=1, flags=flags)
    if n != 1:
        raise RuntimeError(f'{label}: expected 1 replacement, got {n}')
    return out

# ---- Desktop JavaFX ----
p = 'desktop-client/src/main/java/com/example/desktop/SectionedCalculatorPane.java'
t = read(p)
new_single = r'''    private Node singleWelder() {
        ChoiceBox<String> type=choice("Transformer / inverter arc welder",
            "Transformer / inverter arc welder","Motor-generator arc welder","Resistance welder");
        ChoiceBox<String> duty=choice("60%","100%","90%","80%","70%","60%","50%","40%","30%","20%","1 hour");
        TextField kva=field("30"), voltage=field("600"), ratedPrimary=field("50"), amp=field("35");

        Runnable sync = () -> {
            boolean resistance=type.getValue().startsWith("Resistance");
            boolean motorGenerator=type.getValue().startsWith("Motor-generator");
            duty.getItems().clear();
            if(resistance){
                duty.getItems().addAll("50%","40%","30%","25%","20%","15%","10%","7.5%","5%");
                duty.setValue("30%");
            } else {
                duty.getItems().addAll("100%","90%","80%","70%","60%","50%","40%","30%","20%","1 hour");
                duty.setValue("60%");
            }
            kva.setDisable(motorGenerator);
            voltage.setDisable(motorGenerator);
            ratedPrimary.setDisable(!motorGenerator);
        };
        type.setOnAction(e -> sync.run());
        sync.run();

        return apiForm("Single welder worksheet",
            "Choose the CEC welder category and duty cycle. Transformer/inverter arc uses Table 42A, motor-generator arc uses Table 42B and Section 28 protection/control rules, and resistance uses Table 42C.",
            "/welder-worksheet","Calculate single welder",
            () -> payload(
                "kva",number(kva),
                "voltage",number(voltage),
                "ratedPrimaryCurrentA",type.getValue().startsWith("Motor-generator")?number(ratedPrimary):0,
                "dutyFactor",welderDutyFactor(type.getValue(),duty.getValue()),
                "type",type.getValue().startsWith("Resistance")?"RESISTANCE":type.getValue().startsWith("Motor-generator")?"MOTOR_GENERATOR":"TRANSFORMER_ARC",
                "selectedConductorAmpacity",number(amp)
            ),
            row("Welder type",type),
            row("Rating (kVA — transformer/inverter or resistance)",kva),
            row("Primary voltage (V — transformer/inverter or resistance)",voltage),
            row("Rated primary current (A — motor-generator)",ratedPrimary),
            row("Nameplate duty cycle",duty),
            row("Selected conductor ampacity (A)",amp)
        );
    }

'''
t = sub_once(r'    private Node singleWelder\(\) \{.*?\n    \}\n\n(?=    private Node multipleWelders\(\))', new_single, t, 'desktop singleWelder', re.S)
new_helper = r'''    private double welderDutyFactor(String type,String duty){
        String d=duty==null?"":duty.trim();
        if(type.startsWith("Resistance")){
            return switch(d){
                case "50%"->.71; case "40%"->.63; case "30%"->.55; case "25%"->.50;
                case "20%"->.45; case "15%"->.39; case "10%"->.32; case "7.5%"->.27; case "5%"->.22;
                default->throw new IllegalArgumentException("Select a resistance-welder duty cycle");
            };
        }
        if(type.startsWith("Motor-generator")){
            return switch(d){
                case "100%"->1.00; case "90%"->.96; case "80%"->.91; case "70%"->.86; case "60%"->.81;
                case "50%"->.75; case "40%"->.69; case "30%"->.62; case "20%"->.55; case "1 hour"->.80;
                default->throw new IllegalArgumentException("Select a motor-generator welder duty cycle");
            };
        }
        return switch(d){
            case "100%"->1.00; case "90%"->.95; case "80%"->.89; case "70%"->.84; case "60%"->.78;
            case "50%"->.71; case "40%"->.63; case "30%"->.55; case "20%"->.45; case "1 hour"->.75;
            default->throw new IllegalArgumentException("Select a transformer/inverter arc-welder duty cycle");
        };
    }

'''
t = sub_once(r'    private double welderDutyFactor\(String type,String duty\)\{.*?\n    \}\n\n(?=    private Button button)', new_helper, t, 'desktop welderDutyFactor', re.S)
write(p, t)

# ---- Portal REST ----
p = 'portal/src/main/java/com/example/remoteportal/web/CalculatorResource.java'
t = read(p)
t = t.replace(
'  public record WelderWorksheetRequest(Double kva,Double voltage,Double dutyFactor,String type,Double selectedConductorAmpacity){}\n  @PostMapping("/welder-worksheet") public Map<String,Object> welderWorksheet(@RequestBody WelderWorksheetRequest r){return worksheets.welder(n(r.kva()),n(r.voltage()),d(r.dutyFactor(),1),r.type()==null?"TRANSFORMER_ARC":r.type(),n(r.selectedConductorAmpacity()));}',
'  public record WelderWorksheetRequest(Double kva,Double voltage,Double ratedPrimaryCurrentA,Double dutyFactor,String type,Double selectedConductorAmpacity){}\n  @PostMapping("/welder-worksheet") public Map<String,Object> welderWorksheet(@RequestBody WelderWorksheetRequest r){return worksheets.welder(n(r.kva()),n(r.voltage()),n(r.ratedPrimaryCurrentA()),d(r.dutyFactor(),1),r.type()==null?"TRANSFORMER_ARC":r.type(),n(r.selectedConductorAmpacity()));}'
)
if 'ratedPrimaryCurrentA,Double dutyFactor' not in t:
    raise RuntimeError('CalculatorResource welder request patch failed')
write(p, t)

# ---- Backend worksheet logic ----
p = 'portal/src/main/java/com/example/remoteportal/service/WorksheetCalculatorService.java'
t = read(p)
new_method = '''  public Map<String,Object> welder(double kva,double voltage,double ratedPrimaryCurrentA,double dutyFactor,String type,double selectedConductorAmpacity){factor(dutyFactor,"Duty-cycle factor");boolean motorGenerator="MOTOR_GENERATOR".equalsIgnoreCase(type);boolean resistance="RESISTANCE".equalsIgnoreCase(type);double input;if(motorGenerator){positive(ratedPrimaryCurrentA,"Rated primary current");input=ratedPrimaryCurrentA;}else{positive(kva,"kVA");positive(voltage,"Voltage");input=kva*1000/voltage;}double effective=input*dutyFactor;LinkedHashMap<String,Object> r=new LinkedHashMap<>();r.put("edition","CEC_2024");r.put("calculator","WELDER_WORKSHEET");r.put("welderType",motorGenerator?"MOTOR_GENERATOR_ARC":resistance?"RESISTANCE":"TRANSFORMER_INVERTER_ARC");r.put("inputCurrentA",round(input));r.put("dutyAdjustedConductorLoadA",round(effective));r.put("dutyTable",motorGenerator?"42B":resistance?"42C":"42A");r.put("selectedConductorAmpacityA",round(selectedConductorAmpacity));if(motorGenerator){r.put("protectionMethod","Use Section 28 motor overcurrent, overload, controller and disconnect rules; Rule 42-012 routes motor-generator welders through Sections 4 and 28.");r.put("reference","CEC 2024 Rule 42-012 and Table 42B; Sections 4 and 28 for protection/control.");r.put("note","Motor-generator arc welders use rated primary current in amperes. The 200% transformer-arc and 300% resistance-welder shortcuts are not applied to this type.");return r;}double mult=resistance?3.0:2.0,sub1=input*mult,sub2=selectedConductorAmpacity>0?selectedConductorAmpacity*mult:0;int common1=nextDown(sub1),common2=selectedConductorAmpacity>0?nextUp(sub2):0,outcome=selectedConductorAmpacity>0?Math.min(common1,common2):common1;r.put("ocpdMultiplier",mult);r.put("subrule1CalculatedA",round(sub1));r.put("subrule1CommonA",common1);r.put("subrule2CalculatedA",round(sub2));r.put("subrule2CommonA",common2);r.put("startingOutcomeA",outcome);r.put("reference",resistance?"CEC 2024 Rules 42-014 and 42-016; Table 42C.":"CEC 2024 Rules 42-006 and 42-008; Table 42A.");r.put("note",resistance?"Resistance-welder worksheet uses the selected Table 42C duty factor and the 300% overcurrent limits.":"Transformer/inverter arc-welder worksheet uses the selected Table 42A duty factor and the 200% overcurrent limits.");return r;}'''
t = sub_once(r'  public Map<String,Object> welder\(double kva,double voltage,double dutyFactor,String type,double selectedConductorAmpacity\)\{.*?return r;\}', new_method, t, 'WorksheetCalculatorService welder', re.S)
write(p, t)

# ---- Web calculator (also Android embedded calculator workspace) ----
p = 'portal/src/main/resources/static/calculators.html'
t = read(p)
new_functions = r'''function welderDutyFactor(type,duty){
  if(type==='Resistance welder')return {'50%':.71,'40%':.63,'30%':.55,'25%':.50,'20%':.45,'15%':.39,'10%':.32,'7.5%':.27,'5%':.22}[duty]??(()=>{throw new Error('Select a resistance-welder duty cycle')})();
  if(type==='Motor-generator arc welder')return {'100%':1.00,'90%':.96,'80%':.91,'70%':.86,'60%':.81,'50%':.75,'40%':.69,'30%':.62,'20%':.55,'1 hour':.80}[duty]??(()=>{throw new Error('Select a motor-generator welder duty cycle')})();
  return {'100%':1.00,'90%':.95,'80%':.89,'70%':.84,'60%':.78,'50%':.71,'40%':.63,'30%':.55,'20%':.45,'1 hour':.75}[duty]??(()=>{throw new Error('Select a transformer/inverter arc-welder duty cycle')})();
}
function welderTypeCode(type){return type==='Resistance welder'?'RESISTANCE':type==='Motor-generator arc welder'?'MOTOR_GENERATOR':'TRANSFORMER_ARC'}
function syncWelderDuty(){const t=s('w_type'),el=document.getElementById('w_duty');if(!el)return;const resistance=t==='Resistance welder',mg=t==='Motor-generator arc welder';const opts=resistance?['50%','40%','30%','25%','20%','15%','10%','7.5%','5%']:['100%','90%','80%','70%','60%','50%','40%','30%','20%','1 hour'];el.innerHTML=opts.map(x=>`<option>${x}</option>`).join('');el.value=resistance?'30%':'60%';const kva=document.getElementById('w_kva'),v=document.getElementById('w_v'),rated=document.getElementById('w_rated');if(kva)kva.disabled=mg;if(v)v.disabled=mg;if(rated)rated.disabled=!mg}
'''
t = sub_once(r'function welderDutyFactor\(type,duty\)\{.*?\nfunction syncWelderDuty\(\)\{.*?\}\n(?=window\.s14Panes)', new_functions, t, 'web welder helper functions', re.S)
new_s42 = r'''window.s42Panes=[work(card('Single welder worksheet',`<div class="grid"><div class="field"><label>Welder type</label><select id="w_type" onchange="syncWelderDuty()"><option>Transformer / inverter arc welder</option><option>Motor-generator arc welder</option><option>Resistance welder</option></select></div>${field('Rating kVA — transformer/inverter or resistance','w_kva',30)}${field('Voltage V — transformer/inverter or resistance','w_v',600)}${field('Rated primary current A — motor-generator','w_rated',50)}<div class="field"><label>Nameplate duty cycle</label><select id="w_duty"><option>100%</option><option>90%</option><option>80%</option><option>70%</option><option selected>60%</option><option>50%</option><option>40%</option><option>30%</option><option>20%</option><option>1 hour</option></select></div>${field('Selected conductor ampacity A','w_amp',35)}</div><p class="note">CEC 2024 categories: transformer/inverter arc (Table 42A), motor-generator arc (Table 42B with Section 28 motor protection/control), and resistance (Table 42C).</p>`),btn('Calculate single welder',"post(`/api/calculators/welder-worksheet`,{kva:n(`w_kva`),voltage:n(`w_v`),ratedPrimaryCurrentA:n(`w_rated`),dutyFactor:welderDutyFactor(s(`w_type`),s(`w_duty`)),type:welderTypeCode(s(`w_type`)),selectedConductorAmpacity:n(`w_amp`)},`o_w`)") ,'o_w'),work(card('Multiple welder feeder — worksheet method',`<p class="note">Each row has input current, duty factor and branch-conductor ampacity. Feeder conductor uses two largest duty-adjusted loads at 100%, then 85%, 70% and 60%. For motor-generator units use Table 42B duty factors; for transformer/inverter use Table 42A; resistance groups follow their specific Section 42 grouping rule.</p><div id="welderRows"></div>`),btn('Calculate multiple-welder feeder','calcWelders()'),'o_wg')];
'''
t = sub_once(r'window\.s42Panes=\[.*?\];\n(?=const html=)', new_s42, t, 'web s42 panes', re.S)
needle = "showSection(initial,document.querySelectorAll('.nav button')[idx]);"
if needle in t and 'syncWelderDuty();' not in t[t.index(needle):t.index(needle)+120]:
    t = t.replace(needle, needle+'syncWelderDuty();', 1)
write(p, t)

print('Welder type parity patch applied successfully.')
