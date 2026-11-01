package com.example.remoteportal.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ElectricalCalculatorService {
  public static final String DEFAULT_EDITION = "CEC_2024";
  private static final LinkedHashMap<String, Double> CU_75C = new LinkedHashMap<>();
  private static final int[] STANDARD_OCPD = {15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000,5000,6000};
  static {
    CU_75C.put("14 AWG",20d); CU_75C.put("12 AWG",25d); CU_75C.put("10 AWG",35d); CU_75C.put("8 AWG",50d);
    CU_75C.put("6 AWG",65d); CU_75C.put("4 AWG",85d); CU_75C.put("3 AWG",100d); CU_75C.put("2 AWG",115d);
    CU_75C.put("1 AWG",130d); CU_75C.put("1/0 AWG",150d); CU_75C.put("2/0 AWG",175d); CU_75C.put("3/0 AWG",200d); CU_75C.put("4/0 AWG",230d);
  }

  public List<Map<String,Object>> editions(){return List.of(Map.of("id",DEFAULT_EDITION,"name","Canadian Electrical Code 2024","status","ACTIVE","dataRevision","starter-3","description","Versioned backend dataset used by the electrician calculator engine."));}

  public Map<String,Object> catalog(String tier){
    List<Map<String,Object>> c=new ArrayList<>();
    c.add(calc("TRANSFORMER","Transformer","Current, ratio, conductors and primary/secondary OCPD","TIER_1",true));
    c.add(calc("MOTOR","Motor","Conductor, overload, disconnect and branch OCPD","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("REFRIGERATION","Refrigeration motor","Compressor MCA/MOCP, conductor and protection worksheet","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("MULTI_MOTOR","Multiple motors","Feeder conductor and feeder OCPD with simultaneous-start allowance","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("CAPACITOR_BANK","Capacitor bank / PF correction","Required kvar, capacitor current, conductor and OCPD","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("WELDER","Welder","Duty-cycle-adjusted conductor load and OCPD worksheet","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("TAPS","Taps & splitters","Ratio, downstream OCPD and length compliance worksheet","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("CONDUIT_FILL","Conduit fill","Raceway area, mixed conductor groups, fill percentage and spare capacity","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("BOX_FILL","Box fill","Conductors, devices, bonds/clamps and remaining box volume","TIER_2",tierAtLeast(tier,"TIER_2")));
    c.add(calc("SERVICE","Service calculations","Multi-component demand load and service-current engine","TIER_3",tierAtLeast(tier,"TIER_3")));
    c.add(calc("BUSBAR","Busbar","Busbar area and strap sizing","TIER_3",tierAtLeast(tier,"TIER_3")));
    c.add(calc("HAZARDOUS","Hazardous locations","Area/classification reference tools","TIER_3",tierAtLeast(tier,"TIER_3")));
    return Map.of("edition",DEFAULT_EDITION,"tier",tier,"dataRevision","starter-3","calculators",c);
  }

  public Map<String,Object> transformer(double kva,double primaryV,double secondaryV,int phases,double designFactor,double primaryOcpdFactor,double secondaryOcpdFactor,String edition){
    validateEdition(edition); positive(kva,"kVA"); positive(primaryV,"Primary voltage"); positive(secondaryV,"Secondary voltage"); phase(phases); factor(designFactor,"Design factor"); factor(primaryOcpdFactor,"Primary OCPD factor"); factor(secondaryOcpdFactor,"Secondary OCPD factor");
    double ip=currentFromKva(kva,primaryV,phases), is=currentFromKva(kva,secondaryV,phases), reqP=ip*designFactor, reqS=is*designFactor;
    LinkedHashMap<String,Object> r=base("TRANSFORMER",edition); r.put("kva",round(kva));r.put("phases",phases);r.put("primaryVoltage",round(primaryV));r.put("secondaryVoltage",round(secondaryV));r.put("voltageRatio",round(primaryV/secondaryV));
    r.put("formula",phases==3?"I = kVA × 1000 ÷ (√3 × V)":"I = kVA × 1000 ÷ V");r.put("primaryCurrentA",round(ip));r.put("secondaryCurrentA",round(is));
    r.put("requiredPrimaryAmpacityA",round(reqP));r.put("requiredSecondaryAmpacityA",round(reqS));r.put("primaryConductorStarter",conductorFor(reqP));r.put("secondaryConductorStarter",conductorFor(reqS));
    addOcpd(r,"primary",ip*primaryOcpdFactor,primaryOcpdFactor); addOcpd(r,"secondary",is*secondaryOcpdFactor,secondaryOcpdFactor);
    r.put("steps",List.of("Calculate primary and secondary full-load current","Apply conductor/design multiplier","Select starter conductor from backend ampacity data","Apply the selected primary and secondary protection multipliers","Round to the next standard OCPD size for the worksheet result"));
    r.put("reference","CEC 2024 transformer protection rules, including Rule 26-256 as applicable; starter conductor lookup uses Table 2 / 75°C copper values");r.put("note","Transformer OCPD limits depend on the protection arrangement, impedance, equipment markings and installation. Multipliers stay visible so the result can be checked against the applicable CEC rule.");return r;
  }

  public Map<String,Object> motor(double flc,double conductorFactor,double overloadFactor,double ocpdFactor,double disconnectFactor,String edition){
    validateEdition(edition);positive(flc,"Motor FLC");factor(conductorFactor,"Conductor factor");factor(overloadFactor,"Overload factor");factor(ocpdFactor,"OCPD factor");factor(disconnectFactor,"Disconnect factor");
    double conductorA=flc*conductorFactor, overloadA=flc*overloadFactor, ocpd=flc*ocpdFactor, disconnect=flc*disconnectFactor;
    LinkedHashMap<String,Object> r=base("MOTOR",edition);r.put("motorFLC_A",round(flc));r.put("requiredConductorAmpacityA",round(conductorA));r.put("starterConductor",conductorFor(conductorA));r.put("overloadSettingA",round(overloadA));r.put("disconnectMinimumA",round(disconnect));addOcpd(r,"branch",ocpd,ocpdFactor);
    r.put("steps",List.of("Start with applicable motor FLC/nameplate current","Apply branch-conductor factor","Calculate overload setting from selected factor","Calculate branch short-circuit/ground-fault OCPD","Calculate minimum disconnect current rating"));
    r.put("reference","CEC 2024 Section 28 motor conductor, overload, disconnect and branch-circuit OCPD rules");r.put("note","Use the applicable CEC motor-current table or nameplate rule. Factors vary by motor, overload device and OCPD type, so each factor remains editable.");return r;
  }

  public Map<String,Object> refrigeration(double rla,Double mca,Double mocp,double conductorFactor,double ocpdFactor,double overloadFactor,String edition){
    validateEdition(edition);positive(rla,"Rated-load current");factor(conductorFactor,"Conductor factor");factor(ocpdFactor,"OCPD factor");factor(overloadFactor,"Overload factor");
    double conductorA=mca!=null&&mca>0?mca:rla*conductorFactor; double ocpdCalc=mocp!=null&&mocp>0?mocp:rla*ocpdFactor; double overload=rla*overloadFactor;
    LinkedHashMap<String,Object> r=base("REFRIGERATION",edition);r.put("ratedLoadCurrentA",round(rla));r.put("mcaProvided",mca!=null&&mca>0);r.put("mocpProvided",mocp!=null&&mocp>0);r.put("minimumCircuitAmpacityA",round(conductorA));r.put("starterConductor",conductorFor(conductorA));r.put("overloadWorksheetA",round(overload));r.put("ocpdCalculatedA",round(ocpdCalc));r.put("ocpdStandardA",mocp!=null&&mocp>0?nextStandardNotAbove(ocpdCalc):nextStandard(ocpdCalc));
    r.put("steps",List.of("Use equipment nameplate RLA and, where supplied, MCA/MOCP","If MCA is present, use it as the conductor minimum; otherwise apply the selected conductor factor","If MOCP is present, treat it as the equipment maximum protection value; otherwise calculate from the selected factor","Calculate an overload worksheet value separately from branch OCPD"));
    r.put("reference","CEC 2024 motor/refrigeration and air-conditioning equipment rules; equipment nameplate MCA/MOCP values take priority where applicable");r.put("note","This is a compressor/refrigeration worksheet. Final conductor and protection selection must follow the equipment nameplate and the specific CEC rule applicable to the refrigeration or air-conditioning equipment.");return r;
  }

  public Map<String,Object> multiMotor(List<Double> flcs,double largestConductorFactor,double largestBranchOcpdFactor,double simultaneousStartExtra,String edition){
    validateEdition(edition);if(flcs==null||flcs.isEmpty())throw new IllegalArgumentException("Enter at least one motor FLC");factor(largestConductorFactor,"Largest-motor conductor factor");factor(largestBranchOcpdFactor,"Largest branch OCPD factor");if(simultaneousStartExtra<0||simultaneousStartExtra>3)throw new IllegalArgumentException("Simultaneous-start allowance must be between 0 and 3.0");
    double sum=0,largest=0;for(Double v:flcs){if(v==null||v<=0)throw new IllegalArgumentException("All motor FLC values must be greater than zero");sum+=v;largest=Math.max(largest,v);}double others=sum-largest;
    double feederAmpacity=largest*largestConductorFactor+others+(largest*simultaneousStartExtra);double feederOcpd=largest*largestBranchOcpdFactor+others+(largest*simultaneousStartExtra);
    LinkedHashMap<String,Object> r=base("MULTI_MOTOR",edition);r.put("motorCount",flcs.size());r.put("sumFLC_A",round(sum));r.put("largestMotorFLC_A",round(largest));r.put("otherMotorsFLC_A",round(others));r.put("simultaneousStartExtraA",round(largest*simultaneousStartExtra));r.put("feederAmpacityA",round(feederAmpacity));r.put("starterFeederConductor",conductorFor(feederAmpacity));r.put("feederOcpdCalculatedA",round(feederOcpd));r.put("feederOcpdStandardA",nextStandard(feederOcpd));r.put("reference","CEC 2024 Rule 28-204 and related Section 28 feeder/OCPD rules");r.put("note","The simultaneous-start field is an explicit worksheet allowance, not an automatic CEC value. Enter 0 unless the applicable design/rule requires additional starting allowance.");return r;
  }

  public Map<String,Object> capacitorBank(double kw,double existingPf,double targetPf,double voltage,int phases,double conductorFactor,double ocpdFactor,String edition){
    validateEdition(edition);positive(kw,"kW");positive(voltage,"Voltage");phase(phases);pf(existingPf,"Existing PF");pf(targetPf,"Target PF");if(targetPf<=existingPf)throw new IllegalArgumentException("Target PF must be greater than existing PF");factor(conductorFactor,"Conductor factor");factor(ocpdFactor,"OCPD factor");
    double kvar=kw*(Math.tan(Math.acos(existingPf))-Math.tan(Math.acos(targetPf))); double amps=phases==3?kvar*1000/(Math.sqrt(3)*voltage):kvar*1000/voltage; double conductor=amps*conductorFactor; double ocpd=amps*ocpdFactor;
    LinkedHashMap<String,Object> r=base("CAPACITOR_BANK",edition);r.put("realPowerKW",round(kw));r.put("existingPowerFactor",round(existingPf));r.put("targetPowerFactor",round(targetPf));r.put("requiredKvar",round(kvar));r.put("capacitorCurrentA",round(amps));r.put("requiredConductorAmpacityA",round(conductor));r.put("starterConductor",conductorFor(conductor));addOcpd(r,"capacitor",ocpd,ocpdFactor);r.put("formula","kvar = kW × [tan(cos⁻¹ PF1) − tan(cos⁻¹ PF2)]");r.put("reference","CEC 2024 capacitor installation/protection rules and applicable conductor/OCPD requirements");r.put("note","Check capacitor manufacturer requirements, switching/inrush, harmonics and applicable CEC capacitor rules before final equipment selection.");return r;
  }

  public Map<String,Object> welder(double ratedInputA,double dutyCycleFactor,double conductorFactor,double ocpdFactor,int units,double groupDemandFactor,String edition){
    validateEdition(edition);positive(ratedInputA,"Rated input current");if(dutyCycleFactor<=0||dutyCycleFactor>1.5)throw new IllegalArgumentException("Duty-cycle factor must be greater than 0 and no more than 1.5");factor(conductorFactor,"Conductor factor");factor(ocpdFactor,"OCPD factor");if(units<1)throw new IllegalArgumentException("Welder count must be at least 1");if(groupDemandFactor<=0||groupDemandFactor>1)throw new IllegalArgumentException("Group demand factor must be between 0 and 1");
    double effectiveEach=ratedInputA*dutyCycleFactor*conductorFactor;double group=effectiveEach*units*groupDemandFactor;double ocpd=ratedInputA*ocpdFactor;
    LinkedHashMap<String,Object> r=base("WELDER",edition);r.put("ratedInputCurrentEachA",round(ratedInputA));r.put("dutyCycleFactor",round(dutyCycleFactor));r.put("effectiveConductorLoadEachA",round(effectiveEach));r.put("welderCount",units);r.put("groupDemandFactor",round(groupDemandFactor));r.put("groupConductorLoadA",round(group));r.put("starterGroupConductor",conductorFor(group));r.put("singleWelderOcpdCalculatedA",round(ocpd));r.put("singleWelderOcpdStandardA",nextStandard(ocpd));r.put("reference","CEC 2024 welder rules and duty-cycle/demand tables as applicable");r.put("note","Enter the duty-cycle and group-demand factors from the applicable CEC welder table/rule or equipment data. They are intentionally not hard-coded until the licensed/validated table dataset is loaded.");return r;
  }

  public Map<String,Object> tap(double sourceOcpd,double tapAmpacity,double minimumRatio,double lengthM,Double downstreamOcpd,String edition){
    validateEdition(edition);positive(sourceOcpd,"Source OCPD");positive(tapAmpacity,"Tap ampacity");positive(minimumRatio,"Minimum ratio");if(lengthM<0)throw new IllegalArgumentException("Length cannot be negative");double minimum=sourceOcpd*minimumRatio;boolean downstreamOk=downstreamOcpd==null||downstreamOcpd<=0||downstreamOcpd<=tapAmpacity;
    LinkedHashMap<String,Object> r=base("TAPS",edition);r.put("sourceOcpdA",round(sourceOcpd));r.put("tapAmpacityA",round(tapAmpacity));r.put("minimumTapAmpacityA",round(minimum));r.put("lengthM",round(lengthM));r.put("passesRatio",tapAmpacity>=minimum);r.put("downstreamOcpdA",downstreamOcpd==null?0:round(downstreamOcpd));r.put("passesDownstreamOcpdCheck",downstreamOk);r.put("overallWorksheetPass",tapAmpacity>=minimum&&downstreamOk);r.put("reference","CEC 2024 tap-conductor rules; select the ratio and maximum-length condition matching the installation");r.put("note","This checks ratio and downstream OCPD only. Termination, enclosure, length, mechanical protection and the exact tap-rule conditions still need verification.");return r;
  }

  public Map<String,Object> conduitFill(double racewayArea,List<Double> conductorAreas,List<Integer> counts,double maxFillPercent,String edition){
    validateEdition(edition);positive(racewayArea,"Raceway area");if(conductorAreas==null||counts==null||conductorAreas.isEmpty()||conductorAreas.size()!=counts.size())throw new IllegalArgumentException("Provide matching conductor-area and count groups");if(maxFillPercent<=0||maxFillPercent>100)throw new IllegalArgumentException("Fill percentage must be between 0 and 100");double used=0;int total=0;for(int x=0;x<conductorAreas.size();x++){positive(conductorAreas.get(x),"Conductor area");if(counts.get(x)<1)throw new IllegalArgumentException("Each conductor count must be at least 1");used+=conductorAreas.get(x)*counts.get(x);total+=counts.get(x);}double allowed=racewayArea*maxFillPercent/100d;
    LinkedHashMap<String,Object> r=base("CONDUIT_FILL",edition);r.put("racewayArea",round(racewayArea));r.put("totalConductors",total);r.put("usedArea",round(used));r.put("allowedArea",round(allowed));r.put("spareArea",round(allowed-used));r.put("actualFillPercent",round(used/racewayArea*100d));r.put("passes",used<=allowed);r.put("reference","CEC 2024 raceway fill tables/rules; use conductor and raceway areas from the matching code tables");return r;
  }

  public Map<String,Object> boxFill(double boxVolume,double allowance,int insulatedConductors,int deviceEquivalents,int bondClampEquivalents,String edition){
    validateEdition(edition);positive(boxVolume,"Box volume");positive(allowance,"Allowance");if(insulatedConductors<0||deviceEquivalents<0||bondClampEquivalents<0)throw new IllegalArgumentException("Equivalent counts cannot be negative");int total=insulatedConductors+deviceEquivalents+bondClampEquivalents;double required=allowance*total;
    LinkedHashMap<String,Object> r=base("BOX_FILL",edition);r.put("insulatedConductors",insulatedConductors);r.put("deviceEquivalents",deviceEquivalents);r.put("bondClampEquivalents",bondClampEquivalents);r.put("totalEquivalents",total);r.put("requiredVolume",round(required));r.put("remainingVolume",round(boxVolume-required));r.put("passes",boxVolume>=required);r.put("reference","CEC 2024 box-fill rules and applicable conductor/device/bond volume allowances");return r;
  }

  public Map<String,Object> service(List<Double> loadsVA,List<Double> demandFactors,double voltage,int phases,double sparePercent,String edition){
    validateEdition(edition);if(loadsVA==null||demandFactors==null||loadsVA.isEmpty()||loadsVA.size()!=demandFactors.size())throw new IllegalArgumentException("Provide matching load and demand-factor components");positive(voltage,"Voltage");phase(phases);if(sparePercent<0||sparePercent>100)throw new IllegalArgumentException("Spare percentage must be between 0 and 100");double connected=0,demand=0;for(int x=0;x<loadsVA.size();x++){if(loadsVA.get(x)<0)throw new IllegalArgumentException("Loads cannot be negative");if(demandFactors.get(x)<0||demandFactors.get(x)>1)throw new IllegalArgumentException("Demand factors must be 0 to 1");connected+=loadsVA.get(x);demand+=loadsVA.get(x)*demandFactors.get(x);}double design=demand*(1+sparePercent/100d);double amps=phases==3?design/(Math.sqrt(3)*voltage):design/voltage;
    LinkedHashMap<String,Object> r=base("SERVICE",edition);r.put("componentCount",loadsVA.size());r.put("connectedLoadVA",round(connected));r.put("demandLoadVA",round(demand));r.put("sparePercent",round(sparePercent));r.put("designLoadVA",round(design));r.put("serviceCurrentA",round(amps));r.put("starterConductor",conductorFor(amps));r.put("reference","CEC 2024 Section 8 demand calculations; occupancy-specific demand rules must be applied to each component");r.put("note","Use this as a component-based worksheet. Dedicated dwelling, apartment, hotel, school and hospital calculators should populate the components and demand factors from their applicable rules.");return r;
  }

  private static void addOcpd(Map<String,Object>r,String prefix,double calc,double factor){r.put(prefix+"OcpdFactor",round(factor));r.put(prefix+"OcpdCalculatedA",round(calc));r.put(prefix+"OcpdStandardA",nextStandard(calc));}
  private static double currentFromKva(double kva,double v,int phases){return phases==3?kva*1000/(Math.sqrt(3)*v):kva*1000/v;}
  private static LinkedHashMap<String,Object> base(String calculator,String edition){LinkedHashMap<String,Object>r=new LinkedHashMap<>();r.put("edition",edition);r.put("dataRevision","starter-3");r.put("calculator",calculator);return r;}
  private static Map<String,Object> calc(String id,String name,String description,String minimumTier,boolean unlocked){return Map.of("id",id,"name",name,"description",description,"minimumTier",minimumTier,"unlocked",unlocked);}
  private static void validateEdition(String edition){if(!DEFAULT_EDITION.equals(edition))throw new IllegalArgumentException("Unsupported code edition");}
  private static void positive(double v,String n){if(v<=0)throw new IllegalArgumentException(n+" must be greater than zero");}
  private static void factor(double v,String n){if(v<=0||v>6)throw new IllegalArgumentException(n+" must be greater than zero and no more than 6.0");}
  private static void phase(int p){if(p!=1&&p!=3)throw new IllegalArgumentException("Phases must be 1 or 3");}
  private static void pf(double v,String n){if(v<=0||v>1)throw new IllegalArgumentException(n+" must be greater than 0 and no more than 1.0");}
  private static Map<String,Object> conductorFor(double amps){for(var e:CU_75C.entrySet())if(e.getValue()>=amps)return Map.of("size",e.getKey(),"ampacityA",e.getValue(),"found",true);return Map.of("size","Above starter dataset","ampacityA",0,"found",false);}
  private static int nextStandard(double amps){for(int a:STANDARD_OCPD)if(a>=amps)return a;return(int)Math.ceil(amps);}
  private static int nextStandardNotAbove(double amps){int best=STANDARD_OCPD[0];for(int a:STANDARD_OCPD){if(a>amps)break;best=a;}return best;}
  private static double round(double v){return Math.round(v*100d)/100d;}
  private static boolean tierAtLeast(String tier,String minimum){return tierRank(tier)>=tierRank(minimum);}
  private static int tierRank(String tier){return switch(tier==null?"TIER_1":tier){case"ADMIN","TIER_3"->3;case"TIER_2"->2;default->1;};}
}
