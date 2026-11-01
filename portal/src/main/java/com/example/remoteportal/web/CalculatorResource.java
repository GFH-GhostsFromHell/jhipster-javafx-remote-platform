package com.example.remoteportal.web;

import com.example.remoteportal.service.ClientAccountStore;
import com.example.remoteportal.service.ElectricalCalculatorService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/calculators")
public class CalculatorResource {
  private final ElectricalCalculatorService calculators;
  private final ClientAccountStore accounts;

  public CalculatorResource(ElectricalCalculatorService calculators, ClientAccountStore accounts) {
    this.calculators = calculators;
    this.accounts = accounts;
  }

  @GetMapping("/editions") public List<Map<String,Object>> editions(){return calculators.editions();}
  @GetMapping("/catalog") public Map<String,Object> catalog(Principal principal){String tier;try{tier=accounts.tier(principal.getName());}catch(Exception ex){tier="ADMIN";}return calculators.catalog(tier);}

  public record TransformerRequest(Double kva,Double primaryVoltage,Double secondaryVoltage,Integer phases,Double designFactor,Double primaryOcpdFactor,Double secondaryOcpdFactor,String edition){}
  @PostMapping("/transformer") public Map<String,Object> transformer(@RequestBody TransformerRequest r){return calculators.transformer(n(r.kva()),n(r.primaryVoltage()),n(r.secondaryVoltage()),r.phases()==null?3:r.phases(),r.designFactor()==null?1.25:r.designFactor(),r.primaryOcpdFactor()==null?1.25:r.primaryOcpdFactor(),r.secondaryOcpdFactor()==null?1.25:r.secondaryOcpdFactor(),edition(r.edition()));}

  public record MotorRequest(Double flc,Double conductorFactor,Double overloadFactor,Double ocpdFactor,String edition){}
  @PostMapping("/motor") public Map<String,Object> motor(@RequestBody MotorRequest r){return calculators.motor(n(r.flc()),r.conductorFactor()==null?1.25:r.conductorFactor(),r.overloadFactor()==null?1.15:r.overloadFactor(),r.ocpdFactor()==null?1.75:r.ocpdFactor(),edition(r.edition()));}

  public record MultiMotorRequest(List<Double> flcs,Double largestConductorFactor,Double largestBranchOcpdFactor,String edition){}
  @PostMapping("/multi-motor") public Map<String,Object> multiMotor(@RequestBody MultiMotorRequest r){return calculators.multiMotor(r.flcs(),r.largestConductorFactor()==null?1.25:r.largestConductorFactor(),r.largestBranchOcpdFactor()==null?1.75:r.largestBranchOcpdFactor(),edition(r.edition()));}

  public record TapRequest(Double sourceOcpd,Double tapAmpacity,Double minimumRatio,Double lengthM,String edition){}
  @PostMapping("/tap") public Map<String,Object> tap(@RequestBody TapRequest r){return calculators.tap(n(r.sourceOcpd()),n(r.tapAmpacity()),r.minimumRatio()==null?(1d/3d):r.minimumRatio(),r.lengthM()==null?0:r.lengthM(),edition(r.edition()));}

  public record ConduitFillRequest(Double racewayArea,Double conductorArea,Integer count,Double maxFillPercent,String edition){}
  @PostMapping("/conduit-fill") public Map<String,Object> conduitFill(@RequestBody ConduitFillRequest r){return calculators.conduitFill(n(r.racewayArea()),n(r.conductorArea()),r.count()==null?1:r.count(),r.maxFillPercent()==null?40:r.maxFillPercent(),edition(r.edition()));}

  public record BoxFillRequest(Double boxVolume,Double conductorAllowance,Integer conductorEquivalents,String edition){}
  @PostMapping("/box-fill") public Map<String,Object> boxFill(@RequestBody BoxFillRequest r){return calculators.boxFill(n(r.boxVolume()),n(r.conductorAllowance()),r.conductorEquivalents()==null?1:r.conductorEquivalents(),edition(r.edition()));}

  public record ServiceRequest(Double baseLoadVA,Double baseDemand,Double additionalLoadVA,Double additionalDemand,Double voltage,Integer phases,String edition){}
  @PostMapping("/service") public Map<String,Object> service(@RequestBody ServiceRequest r){return calculators.service(z(r.baseLoadVA()),r.baseDemand()==null?1:r.baseDemand(),z(r.additionalLoadVA()),r.additionalDemand()==null?1:r.additionalDemand(),n(r.voltage()),r.phases()==null?1:r.phases(),edition(r.edition()));}

  private static double n(Double v){return v==null?0:v;}
  private static double z(Double v){return v==null?0:v;}
  private static String edition(String e){return e==null||e.isBlank()?ElectricalCalculatorService.DEFAULT_EDITION:e;}
}
