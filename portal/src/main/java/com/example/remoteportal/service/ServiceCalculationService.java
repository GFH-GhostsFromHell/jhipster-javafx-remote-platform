package com.example.remoteportal.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ServiceCalculationService {
  public static final String EDITION="CEC_2024";
  private static final int[] STANDARD_SERVICE={60,100,125,150,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000};

  public Map<String,Object> residential(double ground,double upper,double basement,double rangeKw,double roomHeatKw,double centralHeatKw,double acKw,boolean interlocked,double otherOver1500Kw,double dedicated100Kw,double evseKw,double voltage){
    positive(ground+upper,"Above-ground living area"); positive(voltage,"Voltage");
    double living=ground+upper+Math.max(0,basement)*0.75;
    double basic=5000+Math.ceil(Math.max(0,living-90)/90.0)*1000;
    double range=rangeKw<=0?0:6000+Math.max(0,rangeKw-12)*1000*0.40;
    double roomHeat=roomHeatKw<=10?roomHeatKw*1000:10000+(roomHeatKw-10)*1000*0.75;
    double heat=roomHeat+Math.max(0,centralHeatKw)*1000;
    double ac=Math.max(0,acKw)*1000;
    double hvac=interlocked?Math.max(heat,ac):heat+ac;
    double other=Math.max(0,otherOver1500Kw)*1000;
    double otherDemand=rangeKw>0?other*0.25:(other<=6000?other:6000+(other-6000)*0.25);
    double calc=basic+range+hvac+otherDemand+Math.max(0,dedicated100Kw)*1000+Math.max(0,evseKw)*1000;
    double minimum=(ground+upper)>=80?24000:14400;
    double demand=Math.max(calc,minimum), amps=demand/voltage;
    return result("RESIDENTIAL",demand,amps,voltage,1,Map.ofEntries(
      Map.entry("livingAreaM2",r(living)),Map.entry("basicLoadW",r(basic)),Map.entry("rangeDemandW",r(range)),Map.entry("heatingDemandW",r(heat)),Map.entry("airConditioningW",r(ac)),Map.entry("hvacDemandW",r(hvac)),Map.entry("otherLoadsDemandW",r(otherDemand)),Map.entry("minimumRuleLoadW",r(minimum)),Map.entry("calculatedBeforeMinimumW",r(calc)),Map.entry("reference","CEC 2024 Rules 8-110 and 8-200; Section 62 heating demand; Rule 8-106 interlocked heating/A/C"),Map.entry("note","Basement area is counted at 75%. Range demand is 6000 W plus 40% of rating above 12 kW. Loads over 1500 W use the Rule 8-200 range/no-range treatment. Verify EVEMS and special-load exceptions where applicable.")));
  }

  public Map<String,Object> apartment(int suites,double suiteArea,double rangeKw,double suiteOtherKw,double suiteHeatKw,double suiteAcKw,boolean suiteInterlocked,double houseLoadKw,double houseDemandFactor,double houseContinuousFactor,double voltage,int phases){
    if(suites<1)throw new IllegalArgumentException("Suite count must be at least 1"); positive(suiteArea,"Suite area"); positive(voltage,"Voltage"); phase(phases); if(houseDemandFactor<=0||houseDemandFactor>1)throw new IllegalArgumentException("House demand factor must be >0 and <=1"); if(houseContinuousFactor<=0||houseContinuousFactor>2)throw new IllegalArgumentException("House continuous factor must be >0 and <=2");
    double basic=suiteArea<=45?3500:suiteArea<=90?5000:5000+Math.ceil((suiteArea-90)/90.0)*1000;
    double range=rangeKw<=0?0:6000+Math.max(0,rangeKw-12)*1000*0.40;
    double heat=section62Heat(suiteHeatKw), ac=Math.max(0,suiteAcKw)*1000, hvac=suiteInterlocked?Math.max(heat,ac):heat+ac;
    double each=basic+range+Math.max(0,suiteOtherKw)*1000+hvac;
    int first=Math.min(suites,1), next65=Math.min(Math.max(suites-1,0),2), next40=Math.min(Math.max(suites-3,0),2), rest25=Math.max(suites-5,0);
    double suiteDemand=each*(first+next65*0.65+next40*0.40+rest25*0.25);
    double houseDemand=Math.max(0,houseLoadKw)*1000*houseDemandFactor*houseContinuousFactor;
    double total=suiteDemand+houseDemand, amps=current(total,voltage,phases);
    return result("APARTMENT",total,amps,voltage,phases,Map.ofEntries(
      Map.entry("suiteCount",suites),Map.entry("suiteAreaM2",r(suiteArea)),Map.entry("calculatedLoadPerSuiteW",r(each)),Map.entry("basicLoadPerSuiteW",r(basic)),Map.entry("rangeDemandPerSuiteW",r(range)),Map.entry("suiteHvacDemandW",r(hvac)),Map.entry("suiteDemandAfterDiversityW",r(suiteDemand)),Map.entry("houseConnectedLoadW",r(Math.max(0,houseLoadKw)*1000)),Map.entry("houseDemandW",r(houseDemand)),Map.entry("firstSuite100Percent",first),Map.entry("nextSuites65Percent",next65),Map.entry("nextSuites40Percent",next40),Map.entry("remainingSuites25Percent",rest25),Map.entry("reference","CEC 2024 Rule 8-202 apartment/multi-unit dwelling calculation; common/house loads calculated separately"),Map.entry("note","This follows the apartment-demand sequence used in your practice set: first suite 100%, next 2 at 65%, next 2 at 40%, remaining suites at 25%. House/common-load demand and continuous-load multiplier are shown separately so they can be adjusted to match the exact question.")));
  }

  public Map<String,Object> school(double totalArea,double classroomArea,double otherKw,double heatKw,double acKw,boolean interlocked,double evseKw,double highPowerCordKw,double voltage,int phases){
    area(totalArea,classroomArea); phase(phases); positive(voltage,"Voltage");
    double basic=classroomArea*50+(totalArea-classroomArea)*10;
    double nonHvac=basic+Math.max(0,otherKw)*1000+Math.max(0,evseKw)*1000+Math.max(0,highPowerCordKw)*1000*0.80;
    double heat=section62Heat(heatKw), ac=Math.max(0,acKw)*1000;
    double demandNoAc=schoolDemand(nonHvac,totalArea)+heat;
    double demandWithAc=schoolDemand(nonHvac+ac,totalArea)+(interlocked?0:heat);
    double demand=interlocked?Math.max(demandNoAc,schoolDemand(nonHvac+ac,totalArea)):demandWithAc;
    double amps=current(demand,voltage,phases);
    return result("SCHOOL",demand,amps,voltage,phases,Map.ofEntries(
      Map.entry("basicLoadW",r(basic)),Map.entry("classroomLoadW",r(classroomArea*50)),Map.entry("remainingAreaLoadW",r((totalArea-classroomArea)*10)),Map.entry("heatingDemandW",r(heat)),Map.entry("airConditioningW",r(ac)),Map.entry("reference","CEC 2024 Rule 8-204"),Map.entry("note","Up to 900 m² the non-heating balance uses 75%. Above 900 m² the balance is converted to W/m², with 75% applied to the first 900 m² and 50% to the excess area. High-power cord-connected load is entered here using the 80% receptacle-rating option.")));
  }

  public Map<String,Object> hospital(double totalArea,double highIntensityArea,double otherKw,double heatKw,double acKw,boolean interlocked,double evseKw,double highPowerCordKw,double voltage,int phases){
    area(totalArea,highIntensityArea); phase(phases); positive(voltage,"Voltage");
    double basic=totalArea*20+highIntensityArea*100;
    double nonHvac=basic+Math.max(0,otherKw)*1000+Math.max(0,evseKw)*1000+Math.max(0,highPowerCordKw)*1000*0.80;
    double heat=section62Heat(heatKw), ac=Math.max(0,acKw)*1000;
    double demandHeat=hospitalDemand(nonHvac,totalArea)+heat;
    double demandAc=hospitalDemand(nonHvac+ac,totalArea)+(interlocked?0:heat);
    double demand=interlocked?Math.max(demandHeat,hospitalDemand(nonHvac+ac,totalArea)):demandAc;
    double amps=current(demand,voltage,phases);
    return result("HOSPITAL",demand,amps,voltage,phases,Map.ofEntries(
      Map.entry("basicAreaLoadW",r(totalArea*20)),Map.entry("highIntensityAdditionalW",r(highIntensityArea*100)),Map.entry("heatingDemandW",r(heat)),Map.entry("airConditioningW",r(ac)),Map.entry("reference","CEC 2024 Rule 8-206"),Map.entry("note","Up to 900 m² the non-heating balance uses 80%. Above 900 m², use 80% of the average W/m² for the first 900 m² and 65% for the excess area. High-intensity area is added at 100 W/m².")));
  }

  public Map<String,Object> other(double area,double wattsPerM2,double tableDemand,double specialKw,double heatKw,double acKw,boolean interlocked,double motorKw,double evseKw,double voltage,int phases,String occupancy){
    positive(area,"Area"); positive(wattsPerM2,"Table 14 W/m²"); if(tableDemand<=0||tableDemand>1)throw new IllegalArgumentException("Table demand factor must be >0 and <=1"); phase(phases); positive(voltage,"Voltage");
    double basic=area*wattsPerM2*tableDemand;
    double heat=section62Heat(heatKw), ac=Math.max(0,acKw)*1000;
    double specials=(Math.max(0,specialKw)+Math.max(0,motorKw)+Math.max(0,evseKw))*1000;
    double hvac=interlocked?Math.max(heat,ac):heat+ac;
    double demand=basic+specials+hvac, amps=current(demand,voltage,phases);
    return result("OTHER_OCCUPANCY",demand,amps,voltage,phases,Map.ofEntries(
      Map.entry("occupancy",occupancy==null?"Other":occupancy),Map.entry("basicTable14LoadW",r(basic)),Map.entry("heatingDemandW",r(heat)),Map.entry("airConditioningW",r(ac)),Map.entry("specialAndMotorLoadsW",r(specials)),Map.entry("reference","CEC 2024 Rule 8-210 and Table 14"),Map.entry("note","Enter the W/m² and demand factor for the exact Table 14 occupancy. Special loads such as heating, A/C, motors, show-window/stage lighting and EVSE require their applicable Code demand treatment and are not assumed to be covered by the area load.")));
  }

  private static double schoolDemand(double balance,double area){if(area<=900)return balance*0.75;double wpm2=balance/area;return wpm2*900*0.75+wpm2*(area-900)*0.50;}
  private static double hospitalDemand(double balance,double area){if(area<=900)return balance*0.80;double wpm2=balance/area;return wpm2*900*0.80+wpm2*(area-900)*0.65;}
  private static double section62Heat(double kw){kw=Math.max(0,kw);return kw<=10?kw*1000:10000+(kw-10)*1000*0.75;}
  private static double current(double watts,double voltage,int phases){return phases==3?watts/(Math.sqrt(3)*voltage):watts/voltage;}
  private static int next(double amps){for(int x:STANDARD_SERVICE)if(x>=amps)return x;return(int)Math.ceil(amps);}
  private static Map<String,Object> result(String type,double watts,double amps,double voltage,int phases,Map<String,Object> details){LinkedHashMap<String,Object> out=new LinkedHashMap<>();out.put("edition",EDITION);out.put("calculator",type);out.put("calculatedDemandW",r(watts));out.put("serviceCurrentA",r(amps));out.put("suggestedStandardServiceA",next(amps));out.put("voltage",r(voltage));out.put("phases",phases);out.putAll(details);return out;}
  private static void area(double total,double part){positive(total,"Total area");if(part<0||part>total)throw new IllegalArgumentException("Sub-area must be between 0 and total area");}
  private static void phase(int p){if(p!=1&&p!=3)throw new IllegalArgumentException("Phases must be 1 or 3");}
  private static void positive(double v,String n){if(v<=0)throw new IllegalArgumentException(n+" must be greater than zero");}
  private static double r(double v){return Math.round(v*100.0)/100.0;}
}
