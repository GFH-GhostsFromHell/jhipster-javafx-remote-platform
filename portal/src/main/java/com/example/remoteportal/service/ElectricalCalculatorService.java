package com.example.remoteportal.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ElectricalCalculatorService {
  public static final String DEFAULT_EDITION = "CEC_2024";
  private static final LinkedHashMap<String, Double> CU_75C = new LinkedHashMap<>();
  static {
    CU_75C.put("14 AWG",20d); CU_75C.put("12 AWG",25d); CU_75C.put("10 AWG",35d); CU_75C.put("8 AWG",50d);
    CU_75C.put("6 AWG",65d); CU_75C.put("4 AWG",85d); CU_75C.put("3 AWG",100d); CU_75C.put("2 AWG",115d);
    CU_75C.put("1 AWG",130d); CU_75C.put("1/0 AWG",150d); CU_75C.put("2/0 AWG",175d); CU_75C.put("3/0 AWG",200d); CU_75C.put("4/0 AWG",230d);
  }

  public List<Map<String,Object>> editions() {
    return List.of(Map.of("id",DEFAULT_EDITION,"name","Canadian Electrical Code 2024","status","ACTIVE","dataRevision","starter-1","description","Versioned backend dataset used by the development calculator engine."));
  }

  public Map<String,Object> catalog(String tier) {
    List<Map<String,Object>> c=new ArrayList<>();
    c.add(calc("TRANSFORMER","Transformer","Transformer current and starter conductor lookup","TIER_1",true));
    c.add(calc("MOTOR","Motor","Motor conductors, overloads and OCPD","TIER_2",atLeast(tier,2)));
    c.add(calc("TAPS","Taps & splitters","Transformer, motor and splitter taps","TIER_2",atLeast(tier,2)));
    c.add(calc("CONDUIT_FILL","Conduit fill","Raceway conductor fill and sizing","TIER_2",atLeast(tier,2)));
    c.add(calc("BOX_FILL","Box fill","Outlet/device box conductor fill","TIER_2",atLeast(tier,2)));
    c.add(calc("SERVICE","Service calculations","Residential and commercial demand calculations","TIER_3",atLeast(tier,3)));
    c.add(calc("BUSBAR","Busbar","Busbar area and strap sizing","TIER_3",atLeast(tier,3)));
    c.add(calc("HAZARDOUS","Hazardous locations","Area/classification reference tools","TIER_3",atLeast(tier,3)));
    return Map.of("edition",DEFAULT_EDITION,"tier",tier,"calculators",c);
  }

  public Map<String,Object> transformer(double kva,double primaryV,double secondaryV,int phases,double designFactor,String edition) {
    if(!DEFAULT_EDITION.equals(edition)) throw new IllegalArgumentException("Unsupported code edition");
    if(kva<=0||primaryV<=0||secondaryV<=0) throw new IllegalArgumentException("kVA and voltages must be greater than zero");
    if(phases!=1&&phases!=3) throw new IllegalArgumentException("Phases must be 1 or 3");
    if(designFactor<1.0||designFactor>2.0) throw new IllegalArgumentException("Design factor must be between 1.0 and 2.0");
    double p=phases==3?(kva*1000.0)/(Math.sqrt(3)*primaryV):(kva*1000.0)/primaryV;
    double s=phases==3?(kva*1000.0)/(Math.sqrt(3)*secondaryV):(kva*1000.0)/secondaryV;
    return Map.ofEntries(
      Map.entry("edition",edition),Map.entry("calculator","TRANSFORMER"),Map.entry("kva",round(kva)),Map.entry("phases",phases),
      Map.entry("primaryVoltage",round(primaryV)),Map.entry("secondaryVoltage",round(secondaryV)),
      Map.entry("formula",phases==3?"I = kVA × 1000 ÷ (√3 × V)":"I = kVA × 1000 ÷ V"),
      Map.entry("primaryCurrentA",round(p)),Map.entry("secondaryCurrentA",round(s)),Map.entry("designFactor",round(designFactor)),
      Map.entry("requiredPrimaryAmpacityA",round(p*designFactor)),Map.entry("requiredSecondaryAmpacityA",round(s*designFactor)),
      Map.entry("primaryConductorStarter",conductorFor(p*designFactor)),Map.entry("secondaryConductorStarter",conductorFor(s*designFactor)),
      Map.entry("reference","CEC 2024 Table 2, 75°C copper lookup (starter dataset)"),
      Map.entry("note","Starter conductor lookup only. Final sizing must also apply the exact CEC rule, termination temperature, correction factors, parallel-conductor rules, and OCPD requirements applicable to the installation.")
    );
  }

  private static Map<String,Object> calc(String id,String name,String description,String minimumTier,boolean unlocked){return Map.of("id",id,"name",name,"description",description,"minimumTier",minimumTier,"unlocked",unlocked);}
  private static Map<String,Object> conductorFor(double amps){for(var e:CU_75C.entrySet())if(e.getValue()>=amps)return Map.of("size",e.getKey(),"ampacityA",e.getValue(),"found",true);return Map.of("size","Above starter dataset","ampacityA",0,"found",false);}
  private static boolean atLeast(String tier,int n){return ("ADMIN".equals(tier)||"TIER_3".equals(tier)?3:"TIER_2".equals(tier)?2:1)>=n;}
  private static double round(double v){return Math.round(v*100.0)/100.0;}
}
