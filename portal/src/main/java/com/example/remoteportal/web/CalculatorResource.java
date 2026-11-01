package com.example.remoteportal.web;

import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.ElectricalCalculatorService;
import com.example.remoteportal.service.WorksheetCalculatorService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/calculators")
public class CalculatorResource {
  private final ElectricalCalculatorService calculators; private final WorksheetCalculatorService worksheets; private final ClientAccountStore accounts;
  public CalculatorResource(ElectricalCalculatorService calculators,WorksheetCalculatorService worksheets,ClientAccountStore accounts){this.calculators=calculators;this.worksheets=worksheets;this.accounts=accounts;}
  @GetMapping("/editions") public List<Map<String,Object>> editions(){return calculators.editions();}
  @GetMapping("/catalog") public Map<String,Object> catalog(Principal p){String tier;try{tier=accounts.tier(p.getName());}catch(Exception ex){tier="ADMIN";}return calculators.catalog(tier);}
  public record TransformerRequest(Double kva,Double primaryVoltage,Double secondaryVoltage,Integer phases,Double designFactor,Double primaryOcpdFactor,Double secondaryOcpdFactor,String edition){}
  @PostMapping("/transformer") public Map<String,Object> transformer(@RequestBody TransformerRequest r){return calculators.transformer(n(r.kva()),n(r.primaryVoltage()),n(r.secondaryVoltage()),r.phases()==null?3:r.phases(),d(r.designFactor(),1.25),d(r.primaryOcpdFactor(),1.25),d(r.secondaryOcpdFactor(),1.25),edition(r.edition()));}
  public record DryTransformerWorksheetRequest(Double kva,Double primaryVoltage,Double secondaryVoltage,Integer phases){}
  @PostMapping("/transformer-dry-worksheet") public Map<String,Object> dryTransformer(@RequestBody DryTransformerWorksheetRequest r){return worksheets.dryTransformer(n(r.kva()),n(r.primaryVoltage()),n(r.secondaryVoltage()),r.phases()==null?3:r.phases());}
  public record HvTransformerWorksheetRequest(Double mva,Double primaryKv,Double secondaryKv,String protection,Double protectionFactor,String material,String wiringMethod,String voltageClass){}
  @PostMapping("/transformer-hv-worksheet") public Map<String,Object> hvTransformer(@RequestBody HvTransformerWorksheetRequest r){return worksheets.hvTransformer(n(r.mva()),n(r.primaryKv()),n(r.secondaryKv()),r.protection()==null?"BREAKER":r.protection(),d(r.protectionFactor(),3.0),r.material(),r.wiringMethod(),r.voltageClass());}
  public record CapacitorWorksheetRequest(Double kvar,Double voltage,Integer phases){}
  @PostMapping("/capacitor-worksheet") public Map<String,Object> capacitorWorksheet(@RequestBody CapacitorWorksheetRequest r){return worksheets.capacitorKvar(n(r.kvar()),n(r.voltage()),r.phases()==null?3:r.phases());}
  public record RefrigerationWorksheetRequest(Double rla,Double lra){}
  @PostMapping("/refrigeration-worksheet") public Map<String,Object> refrigerationWorksheet(@RequestBody RefrigerationWorksheetRequest r){return worksheets.refrigeration(n(r.rla()),r.lra());}
  public record WelderWorksheetRequest(Double kva,Double voltage,Double dutyFactor,String type,Double selectedConductorAmpacity){}
  @PostMapping("/welder-worksheet") public Map<String,Object> welderWorksheet(@RequestBody WelderWorksheetRequest r){return worksheets.welder(n(r.kva()),n(r.voltage()),d(r.dutyFactor(),1),r.type()==null?"TRANSFORMER_ARC":r.type(),n(r.selectedConductorAmpacity()));}
  public record MotorRequest(Double flc,Double conductorFactor,Double overloadFactor,Double ocpdFactor,Double disconnectFactor,String edition){}
  @PostMapping("/motor") public Map<String,Object> motor(@RequestBody MotorRequest r){return calculators.motor(n(r.flc()),d(r.conductorFactor(),1.25),d(r.overloadFactor(),1.15),d(r.ocpdFactor(),1.75),d(r.disconnectFactor(),1.15),edition(r.edition()));}
  public record RefrigerationRequest(Double rla,Double mca,Double mocp,Double conductorFactor,Double ocpdFactor,Double overloadFactor,String edition){}
  @PostMapping("/refrigeration") public Map<String,Object> refrigeration(@RequestBody RefrigerationRequest r){return calculators.refrigeration(n(r.rla()),r.mca(),r.mocp(),d(r.conductorFactor(),1.25),d(r.ocpdFactor(),1.75),d(r.overloadFactor(),1.15),edition(r.edition()));}
  public record MultiMotorRequest(List<Double> flcs,Double largestConductorFactor,Double largestBranchOcpdFactor,Double simultaneousStartExtra,String edition){}
  @PostMapping("/multi-motor") public Map<String,Object> multiMotor(@RequestBody MultiMotorRequest r){return calculators.multiMotor(r.flcs(),d(r.largestConductorFactor(),1.25),d(r.largestBranchOcpdFactor(),1.75),d(r.simultaneousStartExtra(),0),edition(r.edition()));}
  public record CapacitorRequest(Double kw,Double existingPf,Double targetPf,Double voltage,Integer phases,Double conductorFactor,Double ocpdFactor,String edition){}
  @PostMapping("/capacitor-bank") public Map<String,Object> capacitor(@RequestBody CapacitorRequest r){return calculators.capacitorBank(n(r.kw()),n(r.existingPf()),n(r.targetPf()),n(r.voltage()),r.phases()==null?3:r.phases(),d(r.conductorFactor(),1.35),d(r.ocpdFactor(),1.65),edition(r.edition()));}
  public record WelderRequest(Double ratedInputA,Double dutyCycleFactor,Double conductorFactor,Double ocpdFactor,Integer units,Double groupDemandFactor,String edition){}
  @PostMapping("/welder") public Map<String,Object> welder(@RequestBody WelderRequest r){return calculators.welder(n(r.ratedInputA()),d(r.dutyCycleFactor(),1),d(r.conductorFactor(),1),d(r.ocpdFactor(),2),r.units()==null?1:r.units(),d(r.groupDemandFactor(),1),edition(r.edition()));}
  public record TapRequest(Double sourceOcpd,Double tapAmpacity,Double minimumRatio,Double lengthM,Double downstreamOcpd,String edition){}
  @PostMapping("/tap") public Map<String,Object> tap(@RequestBody TapRequest r){return calculators.tap(n(r.sourceOcpd()),n(r.tapAmpacity()),d(r.minimumRatio(),1d/3d),d(r.lengthM(),0),r.downstreamOcpd(),edition(r.edition()));}
  public record ConduitFillRequest(Double racewayArea,List<Double> conductorAreas,List<Integer> counts,Double maxFillPercent,String edition){}
  @PostMapping("/conduit-fill") public Map<String,Object> conduitFill(@RequestBody ConduitFillRequest r){return calculators.conduitFill(n(r.racewayArea()),r.conductorAreas(),r.counts(),d(r.maxFillPercent(),40),edition(r.edition()));}
  public record BoxFillRequest(Double boxVolume,Double conductorAllowance,Integer insulatedConductors,Integer deviceEquivalents,Integer bondClampEquivalents,String edition){}
  @PostMapping("/box-fill") public Map<String,Object> boxFill(@RequestBody BoxFillRequest r){return calculators.boxFill(n(r.boxVolume()),n(r.conductorAllowance()),i(r.insulatedConductors()),i(r.deviceEquivalents()),i(r.bondClampEquivalents()),edition(r.edition()));}
  public record ServiceRequest(List<Double> loadsVA,List<Double> demandFactors,Double voltage,Integer phases,Double sparePercent,String edition){}
  @PostMapping("/service") public Map<String,Object> service(@RequestBody ServiceRequest r){return calculators.service(r.loadsVA(),r.demandFactors(),n(r.voltage()),r.phases()==null?1:r.phases(),d(r.sparePercent(),0),edition(r.edition()));}
  private static double n(Double v){return v==null?0:v;} private static double d(Double v,double def){return v==null?def:v;} private static int i(Integer v){return v==null?0:v;} private static String edition(String e){return e==null||e.isBlank()?ElectricalCalculatorService.DEFAULT_EDITION:e;}
}
