from pathlib import Path
import re

ROOT = Path('.')

def read(path): return (ROOT/path).read_text(encoding='utf-8')
def write(path,text): (ROOT/path).write_text(text,encoding='utf-8')
def sub_once(pattern,repl,text,label,flags=0):
    out,n=re.subn(pattern,repl,text,count=1,flags=flags)
    if n!=1: raise RuntimeError(f'{label}: expected 1 replacement, got {n}')
    return out

# Backend motor calculation
p='portal/src/main/java/com/example/remoteportal/service/ElectricalCalculatorService.java'
t=read(p)
old=re.search(r'  public Map<String,Object> motor\(double flc,double cf,double of,double oc,double df,String e\)\{.*?return r;\}',t,re.S)
if not old: raise RuntimeError('motor method not found')
new='''  public Map<String,Object> motor(double flc,double cf,double of,double oc,double df,String deviceType,boolean failsToStart,String e){ok(e);pos(flc,"Motor FLC");Map<String,Object>r=b("MOTOR",e);r.put("requiredConductorAmpacityA",rd(flc*cf));r.put("starterConductor",cond(flc*cf));r.put("overloadSettingA",rd(flc*of));r.put("disconnectMinimumA",rd(flc*df));double calc=flc*oc;int normal=notAbove(calc);int selected=normal;r.put("branchOcpdCalculatedA",rd(calc));r.put("branchOcpdNormalStandardA",normal);r.put("branchOcpdFactor",rd(oc));r.put("motorFailsToStart",failsToStart);String dt=deviceType==null?"":deviceType.toUpperCase(Locale.ROOT);if(failsToStart&&!dt.startsWith("INSTANTANEOUS")){int candidate=nextHigher(normal);double ceiling=motorStartingReliefCeiling(flc,dt,candidate);boolean allowed=candidate>normal&&candidate<=ceiling+1e-9;r.put("branchOcpdNextStandardCandidateA",candidate);r.put("startingReliefCeilingA",rd(ceiling));r.put("startingReliefAllowed",allowed);if(allowed)selected=candidate;}else if(failsToStart){r.put("startingReliefAllowed",false);r.put("startingReliefNote","Instantaneous-trip motor protection follows Rule 28-210 rather than the Rule 28-200(4) next-size relief path.");}r.put("branchOcpdStandardA",selected);r.put("startingReliefApplied",failsToStart&&selected!=normal);r.put("reference","CEC 2024 Rules 28-200 and 28-210, Table 29, and Table 13 standard ratings as applicable");r.put("note",failsToStart?"Failed-start option checks one next standard OCPD size against the Rule 28-200(4) maximum for the selected device type. Equipment markings under Rule 28-202 still govern where applicable.":"Normal motor branch OCPD uses the standard rating not exceeding the Table 29 calculated maximum; a 15 A device remains the minimum standard value represented by this worksheet.");return r;}'''
t=t[:old.start()]+new+t[old.end():]
marker='private static int notAbove(double a){int b=O[0];for(int v:O){if(v>a)break;b=v;}return b;}'
if marker not in t: raise RuntimeError('helper marker missing')
helpers='''private static int notAbove(double a){int b=O[0];for(int v:O){if(v>a)break;b=v;}return b;}private static int nextHigher(int current){for(int v:O)if(v>current)return v;return current;}private static double motorStartingReliefCeiling(double flc,String deviceType,int candidate){return switch(deviceType){case "TIME_DELAY_FUSE"->flc*2.25;case "NON_TIME_DELAY_FUSE"->flc*(candidate<=600?4.0:3.0);case "INVERSE_TIME_BREAKER"->flc*(candidate<=100?4.0:3.0);default->0;};}'''
t=t.replace(marker,helpers,1)
write(p,t)

# REST request
p='portal/src/main/java/com/example/remoteportal/web/CalculatorResource.java'
t=read(p)
old='''  public record MotorRequest(Double flc,Double conductorFactor,Double overloadFactor,Double ocpdFactor,Double disconnectFactor,String edition){}
  @PostMapping("/motor") public Map<String,Object> motor(@RequestBody MotorRequest r){return calculators.motor(n(r.flc()),d(r.conductorFactor(),1.25),d(r.overloadFactor(),1.15),d(r.ocpdFactor(),1.75),d(r.disconnectFactor(),1.15),edition(r.edition()));}'''
new='''  public record MotorRequest(Double flc,Double conductorFactor,Double overloadFactor,Double ocpdFactor,Double disconnectFactor,String ocpdDeviceType,Boolean motorFailsToStart,String edition){}
  @PostMapping("/motor") public Map<String,Object> motor(@RequestBody MotorRequest r){return calculators.motor(n(r.flc()),d(r.conductorFactor(),1.25),d(r.overloadFactor(),1.15),d(r.ocpdFactor(),1.75),d(r.disconnectFactor(),1.15),r.ocpdDeviceType(),Boolean.TRUE.equals(r.motorFailsToStart()),edition(r.edition()));}'''
if old not in t: raise RuntimeError('CalculatorResource motor request block missing')
t=t.replace(old,new,1)
write(p,t)

# Desktop JavaFX
p='desktop-client/src/main/java/com/example/desktop/SectionedCalculatorPane.java'
t=read(p)
needle='''        ChoiceBox<String> disconnect=choice("Moulded-case switch / circuit breaker — 115% FLC",
            "Moulded-case switch / circuit breaker — 115% FLC",
            "General-use AC switch, single-phase — 125% FLC");
'''
replace=needle+'''        CheckBox failsToStart=new CheckBox("Motor fails to start — try next permitted standard OCPD size (Rule 28-200(4))");
'''
if needle not in t: raise RuntimeError('desktop disconnect block missing')
t=t.replace(needle,replace,1)
t=t.replace('''                    "ocpdFactor",ocpd,
                    "disconnectFactor",disconnect.getValue().contains("125%")?1.25:1.15,
                    "edition","CEC_2024"''','''                    "ocpdFactor",ocpd,
                    "disconnectFactor",disconnect.getValue().contains("125%")?1.25:1.15,
                    "ocpdDeviceType",motorOcpdDeviceCode(protection.getValue()),
                    "motorFailsToStart",failsToStart.isSelected(),
                    "edition","CEC_2024"''',1)
t=t.replace('''            row("Locked-rotor current (A; LRC instantaneous method only)",lockedRotor),
            row("Motor overload selection",overload),''','''            row("Locked-rotor current (A; LRC instantaneous method only)",lockedRotor),
            row("Starting relief",failsToStart),
            row("Motor overload selection",overload),''',1)
helper_marker='''    private double welderDutyFactor(String type,String duty){'''
helper='''    private String motorOcpdDeviceCode(String device){
        if(device==null)return "";
        if(device.startsWith("Time-delay"))return "TIME_DELAY_FUSE";
        if(device.startsWith("Non-time-delay"))return "NON_TIME_DELAY_FUSE";
        if(device.startsWith("Inverse-time"))return "INVERSE_TIME_BREAKER";
        if(device.startsWith("Instantaneous-trip"))return "INSTANTANEOUS_TRIP";
        return "";
    }

'''
if helper_marker not in t: raise RuntimeError('desktop helper insertion marker missing')
t=t.replace(helper_marker,helper+helper_marker,1)
write(p,t)

# Web calculator (Android uses this server-delivered workspace too)
p='portal/src/main/resources/static/calculators.html'
t=read(p)
old_ac="""${select('Disconnect type','ac_disc',['Moulded-case switch / circuit breaker — 115% FLC','General-use AC switch, single-phase — 125% FLC'],'Moulded-case switch / circuit breaker — 115% FLC')}</div><p class=\"note\">The app converts motor type + protective-device type into the Table 29 / Rule 28-210 calculation factor automatically.</p>`),btn('Calculate AC motor',\"post(`/api/calculators/motor`,{flc:n(`ac_flc`),conductorFactor:1.25,overloadFactor:s(`ac_ol`).includes('125%')?1.25:1.15,ocpdFactor:motorOcpdFactor(s(`ac_type`),s(`ac_device`),n(`ac_flc`),parseFloat(s(`ac_lrc`))||null,n(`ac_hp`),false),disconnectFactor:s(`ac_disc`).includes('125%')?1.25:1.15},`o_acm`)\") ,'o_acm'));"""
new_ac="""${select('Disconnect type','ac_disc',['Moulded-case switch / circuit breaker — 115% FLC','General-use AC switch, single-phase — 125% FLC'],'Moulded-case switch / circuit breaker — 115% FLC')}<div class=\"field wide\"><label><input id=\"ac_fail\" type=\"checkbox\"> Motor fails to start — try next permitted standard OCPD size (Rule 28-200(4))</label></div></div><p class=\"note\">Normal Table 29 sizing uses the standard OCPD not exceeding the calculated maximum. Check failed-start relief only when the normal device will not permit starting; instantaneous-trip protection remains under Rule 28-210.</p>`),btn('Calculate AC motor',\"post(`/api/calculators/motor`,{flc:n(`ac_flc`),conductorFactor:1.25,overloadFactor:s(`ac_ol`).includes('125%')?1.25:1.15,ocpdFactor:motorOcpdFactor(s(`ac_type`),s(`ac_device`),n(`ac_flc`),parseFloat(s(`ac_lrc`))||null,n(`ac_hp`),false),disconnectFactor:s(`ac_disc`).includes('125%')?1.25:1.15,ocpdDeviceType:motorOcpdDeviceCode(s(`ac_device`)),motorFailsToStart:b(`ac_fail`)},`o_acm`)\") ,'o_acm'));"""
if old_ac not in t: raise RuntimeError('web AC motor block not found')
t=t.replace(old_ac,new_ac,1)
old_dc="""${select('Disconnect type','dc_disc',['Moulded-case switch / circuit breaker — 115% FLC'],'Moulded-case switch / circuit breaker — 115% FLC')}</div>`),btn('Calculate DC motor',\"post(`/api/calculators/motor`,{flc:n(`dc_flc`),conductorFactor:1.25,overloadFactor:s(`dc_ol`).includes('125%')?1.25:1.15,ocpdFactor:motorOcpdFactor(s(`dc_type`),s(`dc_device`),n(`dc_flc`),null,n(`dc_hp`),true),disconnectFactor:1.15},`o_dcm`)\") ,'o_dcm'));"""
new_dc="""${select('Disconnect type','dc_disc',['Moulded-case switch / circuit breaker — 115% FLC'],'Moulded-case switch / circuit breaker — 115% FLC')}<div class=\"field wide\"><label><input id=\"dc_fail\" type=\"checkbox\"> Motor fails to start — try next permitted standard OCPD size (Rule 28-200(4))</label></div></div>`),btn('Calculate DC motor',\"post(`/api/calculators/motor`,{flc:n(`dc_flc`),conductorFactor:1.25,overloadFactor:s(`dc_ol`).includes('125%')?1.25:1.15,ocpdFactor:motorOcpdFactor(s(`dc_type`),s(`dc_device`),n(`dc_flc`),null,n(`dc_hp`),true),disconnectFactor:1.15,ocpdDeviceType:motorOcpdDeviceCode(s(`dc_device`)),motorFailsToStart:b(`dc_fail`)},`o_dcm`)\") ,'o_dcm'));"""
if old_dc not in t: raise RuntimeError('web DC motor block not found')
t=t.replace(old_dc,new_dc,1)
fn_marker='''function welderDutyFactor(type,duty){'''
fn='''function motorOcpdDeviceCode(device){if(device.startsWith('Time-delay'))return 'TIME_DELAY_FUSE';if(device.startsWith('Non-time-delay'))return 'NON_TIME_DELAY_FUSE';if(device.startsWith('Inverse-time'))return 'INVERSE_TIME_BREAKER';if(device.startsWith('Instantaneous-trip'))return 'INSTANTANEOUS_TRIP';return ''}\n'''
if fn_marker not in t: raise RuntimeError('web helper marker missing')
t=t.replace(fn_marker,fn+fn_marker,1)
write(p,t)

print('Motor starting-relief checkbox patch applied.')
