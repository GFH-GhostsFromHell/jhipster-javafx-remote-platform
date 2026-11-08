package com.example.remoteportal.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ElectricalCalculatorService {
  public static final String DEFAULT_EDITION="CEC_2024";
  private static final LinkedHashMap<String,Double> CU=new LinkedHashMap<>();
  private static final LinkedHashMap<String,Double> AL=new LinkedHashMap<>();
  private static final int[] O={15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000,5000,6000};
  static{
    CU.put("14 AWG",20d);CU.put("12 AWG",25d);CU.put("10 AWG",35d);CU.put("8 AWG",50d);CU.put("6 AWG",65d);CU.put("4 AWG",85d);CU.put("3 AWG",100d);CU.put("2 AWG",115d);CU.put("1 AWG",130d);CU.put("1/0 AWG",150d);CU.put("2/0 AWG",175d);CU.put("3/0 AWG",200d);CU.put("4/0 AWG",230d);CU.put("250 kcmil",255d);CU.put("300 kcmil",285d);CU.put("350 kcmil",310d);CU.put("400 kcmil",335d);CU.put("500 kcmil",380d);CU.put("600 kcmil",420d);CU.put("750 kcmil",475d);CU.put("1000 kcmil",545d);
    AL.put("12 AWG",20d);AL.put("10 AWG",30d);AL.put("8 AWG",40d);AL.put("6 AWG",50d);AL.put("4 AWG",65d);AL.put("3 AWG",75d);AL.put("2 AWG",90d);AL.put("1 AWG",100d);AL.put("1/0 AWG",120d);AL.put("2/0 AWG",135d);AL.put("3/0 AWG",155d);AL.put("4/0 AWG",180d);AL.put("250 kcmil",205d);AL.put("300 kcmil",230d);AL.put("350 kcmil",250d);AL.put("400 kcmil",270d);AL.put("500 kcmil",310d);AL.put("600 kcmil",340d);AL.put("750 kcmil",385d);AL.put("1000 kcmil",445d);
  }
  public List<Map<String,Object>> editions(){return List.of(Map.of(
      "id",DEFAULT_EDITION,"name","Canadian Electrical Code 2024 / Ontario worksheet",
      "status","WORKSHEET_ONLY","dataRevision",ElectricalCodeReferences.DATASET_REVISION,
      "referenceProfileId",ElectricalCodeReferences.PROFILE_ID,
      "numericDatasetVerified",false,
      "numericDatasetVerificationStatus",ElectricalCodeReferences.UNVERIFIED,
      "description","Regulatory reference is confirmed; embedded numerical code table datasets are NOT independently licensed-source-verified."));}
  public Map<String,Object> catalog(String tier){List<Map<String,Object>>c=new ArrayList<>();c.add(x("TRANSFORMER","Transformer","Current, ratio, conductors and primary/secondary OCPD","TIER_1",true));c.add(x("MOTOR","Motor","Conductor, overload, disconnect and branch OCPD","TIER_2",at(tier,2)));c.add(x("REFRIGERATION","Refrigeration motor","Compressor MCA/MOCP, conductor and protection worksheet","TIER_2",at(tier,2)));c.add(x("MULTI_REFRIGERATION","Multiple refrigeration motors","Multiple refrigerant motor-compressors, equipment MCA/MOCP, conductor and protection worksheet","TIER_2",at(tier,2)));c.add(x("MULTI_MOTOR","Multiple motors","Feeder conductor and feeder OCPD with simultaneous-start allowance","TIER_2",at(tier,2)));c.add(x("CAPACITOR_BANK","Capacitor bank / PF correction","Required kvar, capacitor current, conductor and OCPD","TIER_2",at(tier,2)));c.add(x("WELDER","Welder","Duty-cycle-adjusted conductor load and OCPD worksheet","TIER_2",at(tier,2)));c.add(x("TAPS","Taps & splitters","Ratio, downstream OCPD and length compliance worksheet","TIER_2",at(tier,2)));c.add(x("CONDUIT_FILL","Conduit fill","Mixed conductor groups and fill percentage","TIER_2",at(tier,2)));c.add(x("BOX_FILL","Box fill","Conductors, devices and bond/clamp equivalents","TIER_2",at(tier,2)));c.add(x("SERVICE","Service calculations","Multi-component demand load engine","TIER_3",at(tier,3)));return Map.of("edition",DEFAULT_EDITION,"tier",tier,
      "dataRevision",ElectricalCodeReferences.DATASET_REVISION,
      "referenceProfileId",ElectricalCodeReferences.PROFILE_ID,
      "numericDatasetVerificationStatus",ElectricalCodeReferences.UNVERIFIED,
      "calculators",c);}
  /**
   * Preliminary math worksheet, not a device approval. Transformer OCPD
   * next-standard exceptions depend on equipment type, voltage and protection
   * arrangement; never apply a blanket round-up to the secondary device.
   */
  /**
   * Conservative arithmetic check under the termination temperature column.
   * Caller supplies the applicable CEC table ampacity and correction factors:
   * without equipment/installation evidence the app cannot certify a conductor.
   */
  public Map<String,Object> conductorReview(double requiredLoadA,double tableAmpacityA,
       int terminationTemperatureC,int insulationTemperatureC,
       double ambientCorrection,double groupingCorrection,String tableReference,String e) {
    ok(e);pos(requiredLoadA,"Required/design load");pos(tableAmpacityA,"Termination-column table ampacity");
    if(terminationTemperatureC!=60&&terminationTemperatureC!=75&&terminationTemperatureC!=90)
      throw new IllegalArgumentException("Select a verified termination-temperature column: 60, 75 or 90 C.");
    if(insulationTemperatureC!=60&&insulationTemperatureC!=75&&insulationTemperatureC!=90)
      throw new IllegalArgumentException("Conductor insulation rating must be 60, 75 or 90 C.");
    if(insulationTemperatureC<terminationTemperatureC)
      throw new IllegalArgumentException("Insulation temperature rating cannot be lower than the selected termination column.");
    if(tableReference==null||!Set.of("TABLE_1","TABLE_2","TABLE_3","TABLE_4").contains(tableReference))
      throw new IllegalArgumentException("Choose the appropriate CEC conductor ampacity Table 1, 2, 3 or 4.");
    if(!Double.isFinite(ambientCorrection)||ambientCorrection<=0||ambientCorrection>1)
      throw new IllegalArgumentException("Ambient correction factor must be positive and no greater than 1; choose a verified factor.");
    if(!Double.isFinite(groupingCorrection)||groupingCorrection<=0||groupingCorrection>1)
      throw new IllegalArgumentException("Grouping correction factor must be positive and no greater than 1; choose a verified factor.");
    double corrected=checked(tableAmpacityA*ambientCorrection*groupingCorrection,"Corrected conductor ampacity");
    Map<String,Object>r=b("CONDUCTOR_REVIEW",e);
    r.put("tableReference",tableReference.replace("_"," "));
    r.put("selectedTerminationTemperatureC",terminationTemperatureC);
    r.put("conductorInsulationTemperatureC",insulationTemperatureC);
    r.put("enteredTerminationColumnAmpacityA",rd(tableAmpacityA));
    r.put("ambientCorrectionFactor",rd(ambientCorrection));
    r.put("groupingCorrectionFactor",rd(groupingCorrection));
    r.put("combinedCorrectionFactor",rd(ambientCorrection*groupingCorrection));
    r.put("adjustedAmpacityA",rd(corrected));
    r.put("requiredLoadA",rd(requiredLoadA));
    r.put("marginA",rd(corrected-requiredLoadA));
    r.put("meetsEnteredLoadArithmetically",corrected+1e-9>=requiredLoadA);
    r.put("codeComplianceVerified",false);
    r.put("terminationMarkingVerified",false);
    r.put("status",corrected+1e-9>=requiredLoadA
        ?"ARITHMETIC CHECK ONLY — FIELD VERIFICATION REQUIRED"
        :"FAIL — ENTERED DESIGN LOAD EXCEEDS CORRECTED AMPACITY");
    r.put("reference","CEC 2024 Rules 4-004 and 4-006; Tables 1 to 4 and applicable adjustment/correction tables (including Tables 5A and 5C).");
    r.put("note","Provide the ampacity from the selected 60/75/90 C TERMINATION temperature column, not the 90 C insulation column unless terminals are specifically permitted at 90 C. Use verified factors for the installation; this arithmetic result is NOT an approved conductor selection. Confirm markings or applicable Rule 4-006 defaults, raceway/cable method, actual load, neutral counting, equipment limits, bonding, overcurrent protection and voltage drop.");
    return r;
  }

  public Map<String,Object> transformer(double kva,double pv,double sv,int ph,double cf,double pof,double sof,String e){
    ok(e);pos(kva,"kVA");pos(pv,"Primary voltage");pos(sv,"Secondary voltage");
    phase(ph);pos(cf,"Conductor design factor");pos(pof,"Primary protection factor");pos(sof,"Secondary protection factor");
    double ip=checked(cur(kva,pv,ph),"Primary current"),is=checked(cur(kva,sv,ph),"Secondary current");
    double primaryAmpacity=checked(ip*cf,"Primary conductor ampacity");
    double secondaryAmpacity=checked(is*cf,"Secondary conductor ampacity");
    double primaryLimit=checked(ip*pof,"Primary OCPD calculation");
    double secondaryLimit=checked(is*sof,"Secondary OCPD calculation");
    Map<String,Object>r=b("TRANSFORMER",e);
    r.put("formula",ph==3?"I = kVA × 1000 ÷ (√3 × V)":"I = kVA × 1000 ÷ V");
    r.put("voltageRatio",rd(checked(pv/sv,"Voltage ratio")));
    r.put("primaryCurrentA",rd(ip));r.put("secondaryCurrentA",rd(is));
    r.put("requiredPrimaryAmpacityA",rd(primaryAmpacity));r.put("requiredSecondaryAmpacityA",rd(secondaryAmpacity));
    r.put("primaryConductorStarter",cond(primaryAmpacity));r.put("secondaryConductorStarter",cond(secondaryAmpacity));r.put("conductorSelectionVerified",false);r.put("terminationTemperatureChecked",false);
    transformerOcpd(r,"primary",primaryLimit,pof);
    transformerOcpd(r,"secondary",secondaryLimit,sof);
    r.put("primaryNextStandardCandidateA",nextStandardCandidate(primaryLimit));
    r.put("nextStandardExceptionVerified",false);
    r.put("ocpdSelectionStatus","CONSERVATIVE_STANDARD_AT_OR_BELOW_INPUT_LIMIT");
    r.put("codeReviewRequired",true);
    r.put("reference","CEC 2024 Rules 26-250 or 26-254 (protection depends on transformer arrangement), 26-256 (conductor ampacity) and Rules 14-100/14-104");
    r.put("note","PRELIMINARY ONLY. Standard OCPD ratings shown do not exceed the supplied calculation. For a qualifying low-voltage dry-type primary device, Rule 26-254(3) may permit the next standard size; that exception is NOT automatically applied here, and cannot be assumed for secondary protection. Verify transformer type, voltage, primary-only versus primary-and-secondary protection, device type, equipment markings, conductor terminal ratings and applicable Ontario amendments. Conductor lookup is a simplified 75°C copper worksheet, NOT a finished installation design.");
    return r;
  }
  public Map<String,Object> motor(double flc,double cf,double of,double oc,double df,String deviceType,boolean failsToStart,String e){
    ok(e);pos(flc,"Motor FLC");pos(cf,"Conductor factor");pos(of,"Overload factor");
    pos(oc,"Branch OCPD factor");pos(df,"Disconnect factor");
    double conductor=checked(flc*cf,"Required conductor ampacity");
    double overload=checked(flc*of,"Overload setting");
    double disconnect=checked(flc*df,"Disconnect minimum");
    double calc=checked(flc*oc,"Motor branch OCPD calculation");
    Map<String,Object>r=b("MOTOR",e);
    r.put("requiredConductorAmpacityA",rd(conductor));r.put("starterConductor",cond(conductor));r.put("conductorSelectionVerified",false);r.put("terminationTemperatureChecked",false);
    r.put("overloadSettingA",rd(overload));r.put("disconnectMinimumA",rd(disconnect));
    // Rule 28-200(3)(a) allows a minimum 15 A device even when Table 29
    // multiplication yields less than 15 A. Clearly flag this exception.
    boolean minimum15Exception=calc<15;
    int normal=notAbove(calc),selected=normal;
    r.put("branchOcpdCalculatedA",rd(calc));
    r.put("branchOcpdNormalStandardA",normal);r.put("branchOcpdFactor",rd(oc));
    r.put("minimum15AExceptionApplied",minimum15Exception);
    r.put("motorFailsToStart",failsToStart);
    String dt=deviceType==null?"":deviceType.toUpperCase(Locale.ROOT);
    if(failsToStart&&!dt.startsWith("INSTANTANEOUS")){
      int candidate=nextHigher(normal);
      double ceiling=motorStartingReliefCeiling(flc,dt,candidate);
      boolean allowed=candidate>normal&&candidate<=ceiling+1e-9;
      r.put("branchOcpdNextStandardCandidateA",candidate);
      r.put("startingReliefCeilingA",rd(ceiling));r.put("startingReliefAllowed",allowed);
      if(allowed)selected=candidate;
      else if(ceiling<=0)r.put("startingReliefNote","No recognized device type was supplied; Rule 28-200(4) starting relief cannot be established.");
    }else if(failsToStart){
      r.put("startingReliefAllowed",false);
      r.put("startingReliefNote","Instantaneous-trip motor protection follows Rule 28-210 rather than Rule 28-200(4) next-size relief.");
    }
    r.put("branchOcpdStandardA",selected);
    r.put("startingReliefApplied",failsToStart&&selected!=normal);
    r.put("codeReviewRequired",true);
    r.put("reference","CEC 2024 Rules 28-200 and 28-210, Table 29 and standard device ratings (Table 13), as applicable");
    r.put("note",minimum15Exception
        ?"The normal 15 A worksheet result uses the explicit minimum-device exception in Rule 28-200(3)(a); verify equipment markings and suitability. Other motor overload, conductor, disconnect and protection factors are user-selected inputs, not independently code-approved by this worksheet."
        :(failsToStart
        ?"Starting relief is a conditional check, not authorization to increase protection. Verify motor starting behaviour, device type, motor table current, Table 29 and applicable equipment markings."
        :"Normal branch OCPD shown does not exceed the entered Table 29 calculation. Factor inputs and nameplate/table basis require independent confirmation."));
    return r;
  }
  public Map<String,Object> refrigeration(double rla,Double lra,Double mca,Double mocp,double cf,double of,boolean failsToStart,String e){ok(e);pos(rla,"RLA");double locked=lra!=null&&lra>0?lra:rla*6.0;boolean markedMca=mca!=null&&mca>0,markedMocp=mocp!=null&&mocp>0;double ca=markedMca?mca:rla*cf;double derivedOcpd=locked*(failsToStart?0.65:0.50);double op=markedMocp?mocp:derivedOcpd;Map<String,Object>r=b("REFRIGERATION",e);r.put("rlaA",rd(rla));r.put("lraA",rd(locked));r.put("lraWasDerived",lra==null||lra<=0);r.put("motorFailsToStart",failsToStart);r.put("mcaProvided",markedMca);r.put("mocpProvided",markedMocp);r.put("minimumCircuitAmpacityA",rd(ca));r.put("starterConductor",cond(ca));r.put("overloadWorksheetA",rd(rla*of));r.put("ocpdCalculationBasis",markedMocp?"Equipment nameplate MOCP":(failsToStart?"65% of LRA failed-start maximum":"50% of LRA normal maximum"));r.put("ocpdCalculatedA",rd(op));r.put("ocpdStandardA",markedMocp?rd(op):Math.max(15,notAbove(op)));r.put("reference","CEC 2024 Rules 28-704, 28-706, 28-708 and 28-710; marked equipment MCA/MOCP values take priority where applicable");r.put("note",markedMca||markedMocp?"Marked equipment MCA/MOCP values are used where entered. Verify the equipment nameplate and manufacturer instructions.":"When no MOCP is marked, this worksheet uses the refrigerant motor-compressor LRA protection path: 50% LRA normally, or up to 65% LRA when the normal device will not permit starting. Blank LRA is derived as 6 × RLA for worksheet practice only.");return r;}
  public record RefrigerationGroupItem(double rla,double lra,boolean failsToStart,String overloadType){}
  public Map<String,Object> refrigerationGroup(List<RefrigerationGroupItem> compressors,double otherLoadA,Double equipmentMca,Double equipmentMocp,String material,String e){
    ok(e);if(compressors==null||compressors.isEmpty())throw new IllegalArgumentException("Enter at least one refrigerant motor-compressor");if(otherLoadA<0)throw new IllegalArgumentException("Other load current cannot be negative");
    double sumRla=0,largestRla=0,highestBranch=0;int highestIndex=-1;List<Map<String,Object>>details=new ArrayList<>();
    for(int idx=0;idx<compressors.size();idx++){RefrigerationGroupItem c=compressors.get(idx);pos(c.rla(),"Compressor RLA");double locked=c.lra()>0?c.lra():c.rla()*6.0;double limit=locked*(c.failsToStart()?0.65:0.50);int branch=Math.max(15,notAbove(limit));double overload="RELAY".equalsIgnoreCase(c.overloadType())?c.rla()*1.40:c.rla()*1.25;double branchCond=c.rla()*1.25;if(branch>highestBranch){highestBranch=branch;highestIndex=idx;}sumRla+=c.rla();largestRla=Math.max(largestRla,c.rla());Map<String,Object>d=new LinkedHashMap<>();d.put("compressor",idx+1);d.put("rlaA",rd(c.rla()));d.put("lraA",rd(locked));d.put("lraWasDerived",c.lra()<=0);d.put("failsToStart",c.failsToStart());d.put("branchConductorMinimumA",rd(branchCond));d.put("branchOcpdMaximumCalculatedA",rd(limit));d.put("branchOcpdStandardMaximumA",branch);d.put("overloadType","RELAY".equalsIgnoreCase(c.overloadType())?"Current-responsive overload relay":"Other overload device / fuse");d.put("overloadMaximumA",rd(overload));details.add(d);}
    double derivedMca=largestRla*1.25+(sumRla-largestRla)+otherLoadA;boolean markedMca=equipmentMca!=null&&equipmentMca>0;double supplyAmpacity=markedMca?equipmentMca:derivedMca;
    double otherCompressors=highestIndex>=0?sumRla-compressors.get(highestIndex).rla():sumRla;double derivedOcpd=highestBranch+otherCompressors+otherLoadA;int derivedStandard=notAbove(derivedOcpd);boolean markedMocp=equipmentMocp!=null&&equipmentMocp>0;double selectedOcpd=markedMocp?equipmentMocp:derivedStandard;double disconnectMinimum=largestRla*1.15+(sumRla-largestRla)+otherLoadA;
    Map<String,Object>r=b("MULTI_REFRIGERATION",e);r.put("compressorCount",compressors.size());r.put("compressors",details);r.put("totalCompressorRlaA",rd(sumRla));r.put("largestCompressorRlaA",rd(largestRla));r.put("otherMotorOrLoadCurrentA",rd(otherLoadA));r.put("derivedMinimumCircuitAmpacityA",rd(derivedMca));r.put("equipmentMcaProvided",markedMca);r.put("minimumSupplyAmpacityA",rd(supplyAmpacity));r.put("minimumSupplyConductor",condMaterial(supplyAmpacity,material));r.put("highestIndividualBranchOcpdA",rd(highestBranch));r.put("derivedGroupOcpdMaximumA",rd(derivedOcpd));r.put("derivedGroupOcpdStandardMaximumA",derivedStandard);r.put("equipmentMocpProvided",markedMocp);r.put("selectedMaximumOcpdA",rd(selectedOcpd));r.put("groupDisconnectMinimumCurrentA",rd(disconnectMinimum));r.put("reference","CEC 2024 Rules 28-700, 28-706, 28-708, 28-710 and 28-714, with the general Section 28 group/feeder rules including 28-108 and 28-204 as applicable");r.put("note",markedMca||markedMocp?"Marked equipment MCA/MOCP values are used where entered. Verify the equipment nameplate and manufacturer instructions before field selection.":"Worksheet group sizing uses 125% of the largest compressor RLA plus the other compressor RLA values and entered other loads. Group OCPD uses the highest permitted individual compressor branch OCPD plus the other simultaneous loads. Verify equipment markings because packaged HVAC/refrigeration equipment can provide governing MCA/MOCP values.");return r;
  }

  public Map<String,Object> multiMotor(List<Double>f,double cf,double oc,double ss,String e){ok(e);if(f==null||f.isEmpty())throw new IllegalArgumentException("Enter motor FLC values");double sum=0,lg=0;for(double v:f){pos(v,"Motor FLC");sum+=v;lg=Math.max(lg,v);}double others=sum-lg,extra=lg*ss;Map<String,Object>r=b("MULTI_MOTOR",e);r.put("motorCount",f.size());r.put("largestMotorFLC_A",rd(lg));r.put("otherMotorsFLC_A",rd(others));r.put("simultaneousStartExtraA",rd(extra));r.put("feederAmpacityA",rd(lg*cf+others+extra));r.put("starterFeederConductor",cond(lg*cf+others+extra));r.put("feederOcpdCalculatedA",rd(lg*oc+others+extra));r.put("feederOcpdStandardA",next(lg*oc+others+extra));r.put("reference","CEC 2024 Rule 28-204 and related Section 28 feeder/OCPD rules");r.put("note","Simultaneous-start allowance is explicit and should be zero unless the applicable design requires it.");return r;}

  public record MotorGroupItem(double flc,boolean continuous,double dutyFactor,double branchOcpdFactor,String motorType,String deviceType){}
  public Map<String,Object> multiMotorDetailed(List<MotorGroupItem> motors,double simultaneousStartExtra,String material,String e){
    ok(e);if(motors==null||motors.isEmpty())throw new IllegalArgumentException("Enter at least one motor");
    double largestContinuous=0,continuousSum=0,nonContinuousSum=0,totalFlc=0,highestBranch=0;int highestIndex=-1;List<Map<String,Object>>details=new ArrayList<>();
    for(int idx=0;idx<motors.size();idx++){MotorGroupItem m=motors.get(idx);pos(m.flc(),"Motor FLC");double df=m.continuous()?1.0:m.dutyFactor();if(!m.continuous()&&df<=0)throw new IllegalArgumentException("Select a valid Table 27 duty factor for each non-continuous motor");double branchCalc=m.flc()*m.branchOcpdFactor();int branchStd=notAbove(branchCalc);if(branchStd>highestBranch){highestBranch=branchStd;highestIndex=idx;}totalFlc+=m.flc();if(m.continuous()){continuousSum+=m.flc();largestContinuous=Math.max(largestContinuous,m.flc());}else nonContinuousSum+=m.flc()*df;Map<String,Object>d=new LinkedHashMap<>();d.put("motor",idx+1);d.put("motorType",m.motorType());d.put("deviceType",m.deviceType());d.put("flcA",rd(m.flc()));d.put("duty",m.continuous()?"Continuous":"Non-continuous");d.put("dutyFactor",rd(m.continuous()?1.0:df));d.put("branchOcpdCalculatedA",rd(branchCalc));d.put("branchOcpdStandardMaxA",branchStd);details.add(d);}
    double feederAmpacity=(largestContinuous>0?(continuousSum+0.25*largestContinuous):0)+nonContinuousSum;
    double others=highestIndex>=0?totalFlc-motors.get(highestIndex).flc():totalFlc;double feederOcpdCalc=highestBranch+others+Math.max(0,simultaneousStartExtra);int feederOcpdStd=notAbove(feederOcpdCalc);
    Map<String,Object>r=b("MULTI_MOTOR_DETAILED",e);r.put("motorCount",motors.size());r.put("motors",details);r.put("splitterSupplyAmpacityA",rd(feederAmpacity));r.put("minimumSplitterSupplyConductor",condMaterial(feederAmpacity,material));r.put("highestPermittedMotorBranchOcpdA",rd(highestBranch));r.put("feederOcpdCalculatedMaximumA",rd(feederOcpdCalc));r.put("feederOcpdStandardMaximumA",feederOcpdStd);r.put("simultaneousStartExtraA",rd(Math.max(0,simultaneousStartExtra)));r.put("reference","CEC 2024 Rules 28-108 and 28-204; non-continuous motor conductor contributions use the selected Table 27 duty factor");r.put("note","Splitter supply conductor sizing uses 125% of the largest continuous-duty motor plus the other continuous-duty motor FLCs, with Table 27 calculated contributions for non-continuous motors. Feeder OCPD uses the motor permitted the highest branch OCPD plus the FLC of the other motors in operation.");return r;
  }
  public Map<String,Object> motorSplitterTap(double motorFlc,boolean continuous,double dutyFactor,double feederAmpacity,double lengthM,boolean enclosedInMetal,boolean mechanicallyProtected,String material,String e){
    ok(e);pos(motorFlc,"Motor FLC");pos(feederAmpacity,"Feeder/splitter supply ampacity");if(lengthM<0)throw new IllegalArgumentException("Tap length cannot be negative");double branchMinimum=motorFlc*(continuous?1.25:dutyFactor);if(!continuous&&dutyFactor<=0)throw new IllegalArgumentException("Select a valid Table 27 duty factor");double required=feederAmpacity;String path="Full feeder ampacity required";boolean exception=false;
    if(lengthM<=3&&enclosedInMetal){required=branchMinimum;path="Rule 28-110(2)(a): ≤3 m and enclosed in metal";exception=true;}
    else if(lengthM<=7.5&&mechanicallyProtected){required=Math.max(branchMinimum,feederAmpacity/3.0);path="Rule 28-110(2)(b): ≤7.5 m, ≥1/3 feeder ampacity, mechanically protected";exception=true;}
    Map<String,Object>r=b("MOTOR_SPLITTER_TAP",e);r.put("motorFlcA",rd(motorFlc));r.put("motorBranchMinimumAmpacityA",rd(branchMinimum));r.put("feederSplitterSupplyAmpacityA",rd(feederAmpacity));r.put("oneThirdFeederAmpacityA",rd(feederAmpacity/3.0));r.put("tapLengthM",rd(lengthM));r.put("shortTapExceptionQualified",exception);r.put("appliedRulePath",path);r.put("minimumTapAmpacityA",rd(required));r.put("minimumTapConductor",condMaterial(required,material));r.put("reference","CEC 2024 Rule 28-110(2), with Rule 28-106/28-108 motor conductor calculations as applicable");r.put("note",lengthM<=3?"For the ≤3 m exception, the tap must be enclosed in metal. If that condition is not met, this worksheet falls back to feeder ampacity.":lengthM<=7.5?"For the ≤7.5 m exception, the tap must be suitably protected from mechanical damage and cannot be less than one-third of feeder ampacity or the applicable motor/branch calculation, whichever is greater.":"Tap exceeds 7.5 m; the short feeder-tap exceptions in Rule 28-110(2) are not available.");return r;
  }
  public Map<String,Object> capacitorBank(double kw,double p1,double p2,double v,int ph,double cf,double oc,String e){ok(e);pos(kw,"kW");pos(v,"Voltage");phase(ph);if(p1<=0||p1>1||p2<=p1||p2>1)throw new IllegalArgumentException("Power factors are invalid");double kvar=kw*(Math.tan(Math.acos(p1))-Math.tan(Math.acos(p2))),a=ph==3?kvar*1000/(Math.sqrt(3)*v):kvar*1000/v;Map<String,Object>r=b("CAPACITOR_BANK",e);r.put("formula","kvar = kW × [tan(cos⁻¹ PF1) − tan(cos⁻¹ PF2)]");r.put("requiredKvar",rd(kvar));r.put("capacitorCurrentA",rd(a));r.put("requiredConductorAmpacityA",rd(a*cf));r.put("starterConductor",cond(a*cf));ocpd(r,"capacitor",a*oc,oc);r.put("reference","CEC 2024 capacitor installation/protection rules and applicable conductor/OCPD requirements");r.put("note","Check manufacturer requirements, inrush/switching and harmonics before final selection.");return r;}
  public Map<String,Object> welder(double a,double duty,double cf,double oc,int units,double group,String e){ok(e);pos(a,"Input current");if(units<1)throw new IllegalArgumentException("Welder count must be at least 1");double each=a*duty*cf,total=each*units*group;Map<String,Object>r=b("WELDER",e);r.put("effectiveConductorLoadEachA",rd(each));r.put("groupConductorLoadA",rd(total));r.put("starterGroupConductor",cond(total));r.put("singleWelderOcpdCalculatedA",rd(a*oc));r.put("singleWelderOcpdStandardA",next(a*oc));r.put("reference","CEC 2024 welder rules and duty-cycle/demand tables as applicable");r.put("note","Enter validated CEC duty-cycle/group-demand factors; they are not hard-coded in this starter dataset.");return r;}
  public Map<String,Object> tap(double src,double ta,double ratio,double len,Double down,String e){ok(e);pos(src,"Source OCPD");pos(ta,"Tap ampacity");boolean p=ta>=src*ratio,q=down==null||down<=0||down<=ta;Map<String,Object>r=b("TAPS",e);r.put("minimumTapAmpacityA",rd(src*ratio));r.put("passesRatio",p);r.put("passesDownstreamOcpdCheck",q);r.put("overallWorksheetPass",p&&q);r.put("reference","CEC 2024 tap-conductor rules; select the ratio and maximum-length condition matching the installation");r.put("note","Also verify termination, enclosure, length and mechanical-protection conditions.");return r;}
  public Map<String,Object> conduitFill(double area,List<Double>as,List<Integer>cs,double pct,String e){ok(e);pos(area,"Raceway area");if(as==null||cs==null||as.size()!=cs.size()||as.isEmpty())throw new IllegalArgumentException("Provide matching conductor areas and counts");double used=0;int total=0;for(int i=0;i<as.size();i++){used+=as.get(i)*cs.get(i);total+=cs.get(i);}double allowed=area*pct/100;Map<String,Object>r=b("CONDUIT_FILL",e);r.put("totalConductors",total);r.put("usedArea",rd(used));r.put("allowedArea",rd(allowed));r.put("spareArea",rd(allowed-used));r.put("actualFillPercent",rd(used/area*100));r.put("passes",used<=allowed);r.put("reference","CEC 2024 raceway fill rules/tables using matching raceway and conductor areas");return r;}
  public Map<String,Object> boxFill(double vol,double allow,int conds,int dev,int bonds,String e){ok(e);int eq=conds+dev+bonds;double req=allow*eq;Map<String,Object>r=b("BOX_FILL",e);r.put("totalEquivalents",eq);r.put("requiredVolume",rd(req));r.put("remainingVolume",rd(vol-req));r.put("passes",vol>=req);r.put("reference","CEC 2024 box-fill rules and conductor/device/bond allowances");return r;}
  public Map<String,Object> service(List<Double>loads,List<Double>dfs,double v,int ph,double spare,String e){ok(e);if(loads==null||dfs==null||loads.size()!=dfs.size()||loads.isEmpty())throw new IllegalArgumentException("Provide matching loads and demand factors");double connected=0,demand=0;for(int i=0;i<loads.size();i++){connected+=loads.get(i);demand+=loads.get(i)*dfs.get(i);}double design=demand*(1+spare/100),a=ph==3?design/(Math.sqrt(3)*v):design/v;Map<String,Object>r=b("SERVICE",e);r.put("connectedLoadVA",rd(connected));r.put("demandLoadVA",rd(demand));r.put("designLoadVA",rd(design));r.put("serviceCurrentA",rd(a));r.put("starterConductor",cond(a));r.put("reference","CEC 2024 Section 8 demand calculations; occupancy-specific rules must be applied to each component");r.put("note","This is a component worksheet; dedicated dwelling/apartment/hotel/school/hospital rule engines can populate the factors later.");return r;}
  private static Map<String,Object>x(String id,String n,String d,String tier,boolean u){return Map.of("id",id,"name",n,"description",d,"minimumTier",tier,"unlocked",u);}private static Map<String,Object>b(String c,String e){Map<String,Object>r=new LinkedHashMap<>();
    r.put("edition",e);
    r.put("dataRevision",ElectricalCodeReferences.DATASET_REVISION);
    r.put("calculator",c);
    r.put("referenceProfileId",ElectricalCodeReferences.PROFILE_ID);
    r.put("numericDatasetVerificationStatus",ElectricalCodeReferences.UNVERIFIED);
    r.put("numericDatasetVerified",false);
    r.put("codeComplianceVerified",false);
    return r;}private static void ocpd(Map<String,Object>r,String p,double a,double f){r.put(p+"OcpdCalculatedA",rd(a));r.put(p+"OcpdStandardA",next(a));r.put(p+"OcpdFactor",rd(f));}private static void transformerOcpd(Map<String,Object>r,String p,double a,double f){r.put(p+"OcpdCalculatedA",rd(a));Integer standard=standardAtOrBelow(a);r.put(p+"OcpdStandardA",standard);r.put(p+"NoListedStandardWithinLimit",standard==null);r.put(p+"OcpdFactor",rd(f));}private static Integer standardAtOrBelow(double limit){if(!Double.isFinite(limit)||limit<=0)throw new IllegalArgumentException("OCPD limit must be finite and positive");if(limit<15)return null;return notAbove(limit);}private static Integer nextStandardCandidate(double limit){if(!Double.isFinite(limit)||limit<=0)throw new IllegalArgumentException("OCPD limit must be finite and positive");for(int rating:O){if(Math.abs(rating-limit)<1e-8)return null;if(rating>limit)return rating;}return null;}private static double checked(double value,String name){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(name+" is outside the supported finite worksheet range");return value;}private static double cur(double kva,double v,int ph){return ph==3?kva*1000/(Math.sqrt(3)*v):kva*1000/v;}private static Map<String,Object>cond(double a){return condMaterial(a,"COPPER");}private static Map<String,Object>condMaterial(double a,String material){Map<String,Double>m="ALUMINUM".equalsIgnoreCase(material)?AL:CU;String name="ALUMINUM".equalsIgnoreCase(material)?"Aluminum":"Copper";for(var entry:m.entrySet())if(entry.getValue()>=a)return Map.of("material",name,"basis","UNADJUSTED 75°C raceway/cable example only — terminal temperature not verified","size",entry.getKey(),"ampacityA",entry.getValue(),"found",true,"candidateOnly",true,"codeComplianceVerified",false,"requiredReview","Confirm applicable 60°C/75°C terminal column, Table 5A/5C corrections, wiring method, and protection.");return Map.of("material",name,"basis","UNADJUSTED 75°C raceway/cable example only — terminal temperature not verified","size","Above worksheet dataset","ampacityA",0,"found",false,"candidateOnly",true,"codeComplianceVerified",false,"requiredReview","Use appropriate CEC conductor tables and verified termination limits.");}private static int next(double a){for(int v:O)if(v>=a)return v;return(int)Math.ceil(a);}private static int notAbove(double a){int b=O[0];for(int v:O){if(v>a)break;b=v;}return b;}private static int nextHigher(int current){for(int v:O)if(v>current)return v;return current;}private static double motorStartingReliefCeiling(double flc,String deviceType,int candidate){return switch(deviceType){case "TIME_DELAY_FUSE"->flc*2.25;case "NON_TIME_DELAY_FUSE"->flc*(candidate<=600?4.0:3.0);case "INVERSE_TIME_BREAKER"->flc*(candidate<=100?4.0:3.0);default->0;};}private static void ok(String e){if(!DEFAULT_EDITION.equals(e))throw new IllegalArgumentException("Unsupported code edition");}private static void pos(double v,String n){if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(n+" must be finite and greater than zero");}private static void phase(int p){if(p!=1&&p!=3)throw new IllegalArgumentException("Phases must be 1 or 3");}private static double rd(double v){return Math.round(v*100d)/100d;}private static boolean at(String t,int n){int r="ADMIN".equals(t)||"TIER_3".equals(t)?3:"TIER_2".equals(t)?2:1;return r>=n;}
}
