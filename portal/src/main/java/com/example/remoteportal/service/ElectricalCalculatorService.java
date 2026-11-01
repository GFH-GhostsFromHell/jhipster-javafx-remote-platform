package com.example.remoteportal.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ElectricalCalculatorService {
  public static final String DEFAULT_EDITION = "CEC_2024";

  private static final LinkedHashMap<String, Double> CU_75C = new LinkedHashMap<>();
  private static final int[] STANDARD_OCPD = {15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000,5000,6000};
  static {
    CU_75C.put("14 AWG", 20d); CU_75C.put("12 AWG", 25d); CU_75C.put("10 AWG", 35d);
    CU_75C.put("8 AWG", 50d); CU_75C.put("6 AWG", 65d); CU_75C.put("4 AWG", 85d);
    CU_75C.put("3 AWG", 100d); CU_75C.put("2 AWG", 115d); CU_75C.put("1 AWG", 130d);
    CU_75C.put("1/0 AWG", 150d); CU_75C.put("2/0 AWG", 175d); CU_75C.put("3/0 AWG", 200d); CU_75C.put("4/0 AWG", 230d);
  }

  public List<Map<String,Object>> editions() {
    return List.of(Map.of("id", DEFAULT_EDITION,"name", "Canadian Electrical Code 2024","status", "ACTIVE","dataRevision", "starter-2","description", "Versioned backend dataset used by the electrician calculator engine."));
  }

  public Map<String,Object> catalog(String tier) {
    List<Map<String,Object>> c = new ArrayList<>();
    c.add(calc("TRANSFORMER", "Transformer", "Current, conductors and primary/secondary OCPD", "TIER_1", true));
    c.add(calc("MOTOR", "Motor", "Motor conductor, overload and branch OCPD", "TIER_2", tierAtLeast(tier,"TIER_2")));
    c.add(calc("MULTI_MOTOR", "Multiple motors", "Feeder ampacity and feeder OCPD", "TIER_2", tierAtLeast(tier,"TIER_2")));
    c.add(calc("TAPS", "Taps & splitters", "Tap ratio and ampacity compliance checks", "TIER_2", tierAtLeast(tier,"TIER_2")));
    c.add(calc("CONDUIT_FILL", "Conduit fill", "Raceway area, fill percentage and max conductor count", "TIER_2", tierAtLeast(tier,"TIER_2")));
    c.add(calc("BOX_FILL", "Box fill", "Box volume versus conductor/device allowance", "TIER_2", tierAtLeast(tier,"TIER_2")));
    c.add(calc("SERVICE", "Service calculations", "Demand-load and service-current calculator", "TIER_3", tierAtLeast(tier,"TIER_3")));
    c.add(calc("BUSBAR", "Busbar", "Busbar area and strap sizing", "TIER_3", tierAtLeast(tier,"TIER_3")));
    c.add(calc("HAZARDOUS", "Hazardous locations", "Area/classification reference tools", "TIER_3", tierAtLeast(tier,"TIER_3")));
    return Map.of("edition", DEFAULT_EDITION,"tier", tier,"calculators", c);
  }

  private static Map<String,Object> calc(String id,String name,String description,String minimumTier,boolean unlocked){
    return Map.of("id",id,"name",name,"description",description,"minimumTier",minimumTier,"unlocked",unlocked);
  }

  public Map<String,Object> transformer(double kva,double primaryV,double secondaryV,int phases,double designFactor,double primaryOcpdFactor,double secondaryOcpdFactor,String edition){
    validateEdition(edition); positive(kva,"kVA"); positive(primaryV,"Primary voltage"); positive(secondaryV,"Secondary voltage");
    if(phases!=1&&phases!=3) throw new IllegalArgumentException("Phases must be 1 or 3");
    factor(designFactor,"Design factor"); factor(primaryOcpdFactor,"Primary OCPD factor"); factor(secondaryOcpdFactor,"Secondary OCPD factor");
    double root3=Math.sqrt(3.0);
    double ip=phases==3?(kva*1000)/(root3*primaryV):(kva*1000)/primaryV;
    double is=phases==3?(kva*1000)/(root3*secondaryV):(kva*1000)/secondaryV;
    double reqP=ip*designFactor, reqS=is*designFactor;
    double rawPOcpd=ip*primaryOcpdFactor, rawSOcpd=is*secondaryOcpdFactor;
    LinkedHashMap<String,Object> r=base("TRANSFORMER",edition);
    r.put("kva",round(kva)); r.put("phases",phases); r.put("primaryVoltage",round(primaryV)); r.put("secondaryVoltage",round(secondaryV));
    r.put("formula",phases==3?"I = kVA × 1000 ÷ (√3 × V)":"I = kVA × 1000 ÷ V");
    r.put("primaryCurrentA",round(ip)); r.put("secondaryCurrentA",round(is)); r.put("designFactor",round(designFactor));
    r.put("requiredPrimaryAmpacityA",round(reqP)); r.put("requiredSecondaryAmpacityA",round(reqS));
    r.put("primaryConductorStarter",conductorFor(reqP)); r.put("secondaryConductorStarter",conductorFor(reqS));
    r.put("primaryOcpdFactor",round(primaryOcpdFactor)); r.put("primaryOcpdCalculatedA",round(rawPOcpd)); r.put("primaryOcpdStandardA",nextStandard(rawPOcpd));
    r.put("secondaryOcpdFactor",round(secondaryOcpdFactor)); r.put("secondaryOcpdCalculatedA",round(rawSOcpd)); r.put("secondaryOcpdStandardA",nextStandard(rawSOcpd));
    r.put("reference","CEC 2024 transformer protection rules (including Rule 26-256 as applicable) and Table 2 starter conductor dataset");
    r.put("note","Transformer OCPD limits depend on the exact installation and protection arrangement. The selected multipliers are shown explicitly so the result can be checked against the applicable CEC 2024 rule before use.");
    return r;
  }

  public Map<String,Object> motor(double flc,double conductorFactor,double overloadFactor,double ocpdFactor,String edition){
    validateEdition(edition); positive(flc,"Motor FLC"); factor(conductorFactor,"Conductor factor"); factor(overloadFactor,"Overload factor"); factor(ocpdFactor,"OCPD factor");
    double conductorA=flc*conductorFactor, overloadA=flc*overloadFactor, ocpdCalc=flc*ocpdFactor;
    LinkedHashMap<String,Object> r=base("MOTOR",edition);
    r.put("motorFLC_A",round(flc)); r.put("conductorFactor",round(conductorFactor)); r.put("requiredConductorAmpacityA",round(conductorA)); r.put("starterConductor",conductorFor(conductorA));
    r.put("overloadFactor",round(overloadFactor)); r.put("overloadSettingA",round(overloadA)); r.put("ocpdFactor",round(ocpdFactor)); r.put("ocpdCalculatedA",round(ocpdCalc)); r.put("ocpdStandardA",nextStandard(ocpdCalc));
    r.put("reference","CEC 2024 Section 28 motor conductor, overload and branch-circuit OCPD rules");
    r.put("note","Use the CEC motor full-load-current table/nameplate rule applicable to the equipment. OCPD and overload factors vary by device and motor conditions; this calculator keeps those factors visible and editable."); return r;
  }

  public Map<String,Object> multiMotor(List<Double> flcs,double largestConductorFactor,double largestBranchOcpdFactor,String edition){
    validateEdition(edition); if(flcs==null||flcs.isEmpty()) throw new IllegalArgumentException("Enter at least one motor FLC");
    double sum=0,largest=0; for(Double v:flcs){if(v==null||v<=0)throw new IllegalArgumentException("All motor FLC values must be greater than zero");sum+=v;largest=Math.max(largest,v);} double others=sum-largest;
    double feederAmpacity=largest*largestConductorFactor+others; double largestBranch=largest*largestBranchOcpdFactor; double feederOcpdCalc=largestBranch+others;
    LinkedHashMap<String,Object> r=base("MULTI_MOTOR",edition); r.put("motorCount",flcs.size()); r.put("sumFLC_A",round(sum)); r.put("largestMotorFLC_A",round(largest)); r.put("otherMotorsFLC_A",round(others));
    r.put("feederAmpacityA",round(feederAmpacity)); r.put("starterFeederConductor",conductorFor(feederAmpacity)); r.put("largestBranchOcpdCalculatedA",round(largestBranch)); r.put("feederOcpdCalculatedA",round(feederOcpdCalc)); r.put("feederOcpdStandardA",nextStandard(feederOcpdCalc));
    r.put("reference","CEC 2024 Rule 28-204 and related Section 28 feeder/OCPD rules"); r.put("note","This is the common largest-motor-plus-other-motors method. Simultaneous-start and special equipment conditions can change the required result."); return r;
  }

  public Map<String,Object> tap(double sourceOcpd,double tapAmpacity,double minimumRatio,double lengthM,String edition){
    validateEdition(edition); positive(sourceOcpd,"Source OCPD"); positive(tapAmpacity,"Tap ampacity"); positive(minimumRatio,"Minimum ratio"); if(lengthM<0)throw new IllegalArgumentException("Length cannot be negative");
    double minimum=sourceOcpd*minimumRatio; LinkedHashMap<String,Object> r=base("TAPS",edition); r.put("sourceOcpdA",round(sourceOcpd)); r.put("tapAmpacityA",round(tapAmpacity)); r.put("minimumRatio",round(minimumRatio)); r.put("minimumTapAmpacityA",round(minimum)); r.put("lengthM",round(lengthM)); r.put("passesRatio",tapAmpacity>=minimum);
    r.put("reference","CEC 2024 tap-conductor rules; select the ratio and length condition that matches the installation"); r.put("note","Ratio alone does not prove compliance. Termination, enclosure, length, mechanical protection and downstream OCPD conditions must also be satisfied."); return r;
  }

  public Map<String,Object> conduitFill(double racewayArea,double conductorArea,int count,double maxFillPercent,String edition){
    validateEdition(edition); positive(racewayArea,"Raceway area"); positive(conductorArea,"Conductor area"); if(count<1)throw new IllegalArgumentException("Conductor count must be at least 1"); if(maxFillPercent<=0||maxFillPercent>100)throw new IllegalArgumentException("Fill percentage must be between 0 and 100");
    double used=conductorArea*count, allowed=racewayArea*(maxFillPercent/100d), pct=used/racewayArea*100d; int max=(int)Math.floor(allowed/conductorArea);
    LinkedHashMap<String,Object> r=base("CONDUIT_FILL",edition); r.put("racewayArea",round(racewayArea)); r.put("conductorAreaEach",round(conductorArea)); r.put("count",count); r.put("usedArea",round(used)); r.put("maxFillPercent",round(maxFillPercent)); r.put("allowedArea",round(allowed)); r.put("actualFillPercent",round(pct)); r.put("maximumCount",max); r.put("passes",used<=allowed); r.put("reference","CEC 2024 raceway fill tables/rules; use matching conductor and raceway areas from the selected code dataset"); return r;
  }

  public Map<String,Object> boxFill(double boxVolume,double conductorAllowance,int conductorEquivalents,String edition){
    validateEdition(edition); positive(boxVolume,"Box volume"); positive(conductorAllowance,"Conductor allowance"); if(conductorEquivalents<1)throw new IllegalArgumentException("Conductor equivalents must be at least 1");
    double required=conductorAllowance*conductorEquivalents; LinkedHashMap<String,Object> r=base("BOX_FILL",edition); r.put("boxVolume",round(boxVolume)); r.put("allowancePerEquivalent",round(conductorAllowance)); r.put("conductorEquivalents",conductorEquivalents); r.put("requiredVolume",round(required)); r.put("remainingVolume",round(boxVolume-required)); r.put("passes",boxVolume>=required); r.put("reference","CEC 2024 box-fill rules and applicable box/conductor volume allowances"); return r;
  }

  public Map<String,Object> service(double baseLoadVA,double baseDemand,double additionalLoadVA,double additionalDemand,double voltage,int phases,String edition){
    validateEdition(edition); if(baseLoadVA<0||additionalLoadVA<0)throw new IllegalArgumentException("Loads cannot be negative"); if(baseDemand<0||baseDemand>1||additionalDemand<0||additionalDemand>1)throw new IllegalArgumentException("Demand factors must be decimals from 0 to 1"); positive(voltage,"Voltage"); if(phases!=1&&phases!=3)throw new IllegalArgumentException("Phases must be 1 or 3");
    double demandVA=baseLoadVA*baseDemand+additionalLoadVA*additionalDemand; double amps=phases==3?demandVA/(Math.sqrt(3)*voltage):demandVA/voltage;
    LinkedHashMap<String,Object> r=base("SERVICE",edition); r.put("calculatedDemandVA",round(demandVA)); r.put("serviceCurrentA",round(amps)); r.put("starterConductor",conductorFor(amps)); r.put("reference","CEC 2024 Section 8 demand calculations; exact occupancy-specific demand rules must be applied to the load components entered"); r.put("note","This general demand engine is the base for dedicated dwelling, apartment, hotel, school and hospital calculators that will use their specific demand schedules."); return r;
  }

  private static LinkedHashMap<String,Object> base(String calculator,String edition){LinkedHashMap<String,Object> r=new LinkedHashMap<>();r.put("edition",edition);r.put("calculator",calculator);return r;}
  private static void validateEdition(String edition){if(!DEFAULT_EDITION.equals(edition))throw new IllegalArgumentException("Unsupported code edition");}
  private static void positive(double v,String name){if(v<=0)throw new IllegalArgumentException(name+" must be greater than zero");}
  private static void factor(double v,String name){if(v<=0||v>6)throw new IllegalArgumentException(name+" must be greater than zero and no more than 6.0");}
  private static Map<String,Object> conductorFor(double amps){for(Map.Entry<String,Double>e:CU_75C.entrySet())if(e.getValue()>=amps)return Map.of("size",e.getKey(),"ampacityA",e.getValue(),"found",true);return Map.of("size","Above starter dataset","ampacityA",0,"found",false);}
  private static int nextStandard(double amps){for(int a:STANDARD_OCPD)if(a>=amps)return a;return (int)Math.ceil(amps);}
  private static double round(double v){return Math.round(v*100d)/100d;}
  private static boolean tierAtLeast(String tier,String minimum){return tierRank(tier)>=tierRank(minimum);}
  private static int tierRank(String tier){return switch(tier==null?"TIER_1":tier){case "ADMIN","TIER_3"->3;case "TIER_2"->2;default->1;};}
}
