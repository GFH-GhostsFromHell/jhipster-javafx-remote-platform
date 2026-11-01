package com.example.remoteportal.web;

import com.example.remoteportal.service.ServiceCalculationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/calculators/service")
public class ServiceCalculationResource {
  private final ServiceCalculationService service;
  public ServiceCalculationResource(ServiceCalculationService service){this.service=service;}

  public record ResidentialRequest(Double groundArea,Double upperArea,Double basementArea,Double rangeKw,Double roomHeatKw,Double centralHeatKw,Double acKw,Boolean interlocked,Double otherOver1500Kw,Double dedicated100Kw,Double evseKw,Double voltage){}
  @PostMapping("/residential") public Map<String,Object> residential(@RequestBody ResidentialRequest r){return service.residential(z(r.groundArea()),z(r.upperArea()),z(r.basementArea()),z(r.rangeKw()),z(r.roomHeatKw()),z(r.centralHeatKw()),z(r.acKw()),b(r.interlocked()),z(r.otherOver1500Kw()),z(r.dedicated100Kw()),z(r.evseKw()),n(r.voltage(),240));}

  public record ApartmentRequest(Integer suites,Double suiteArea,Double rangeKw,Double suiteOtherKw,Double suiteHeatKw,Double suiteAcKw,Boolean suiteInterlocked,Double houseLoadKw,Double houseDemandFactor,Double houseContinuousFactor,Double voltage,Integer phases){}
  @PostMapping("/apartment") public Map<String,Object> apartment(@RequestBody ApartmentRequest r){return service.apartment(i(r.suites(),1),n(r.suiteArea(),90),z(r.rangeKw()),z(r.suiteOtherKw()),z(r.suiteHeatKw()),z(r.suiteAcKw()),b(r.suiteInterlocked()),z(r.houseLoadKw()),n(r.houseDemandFactor(),0.75),n(r.houseContinuousFactor(),1.25),n(r.voltage(),240),i(r.phases(),1));}

  public record SchoolRequest(Double totalArea,Double classroomArea,Double otherKw,Double heatKw,Double acKw,Boolean interlocked,Double evseKw,Double highPowerCordKw,Double voltage,Integer phases){}
  @PostMapping("/school") public Map<String,Object> school(@RequestBody SchoolRequest r){return service.school(n(r.totalArea(),0),z(r.classroomArea()),z(r.otherKw()),z(r.heatKw()),z(r.acKw()),b(r.interlocked()),z(r.evseKw()),z(r.highPowerCordKw()),n(r.voltage(),240),i(r.phases(),1));}

  public record HospitalRequest(Double totalArea,Double highIntensityArea,Double otherKw,Double heatKw,Double acKw,Boolean interlocked,Double evseKw,Double highPowerCordKw,Double voltage,Integer phases){}
  @PostMapping("/hospital") public Map<String,Object> hospital(@RequestBody HospitalRequest r){return service.hospital(n(r.totalArea(),0),z(r.highIntensityArea()),z(r.otherKw()),z(r.heatKw()),z(r.acKw()),b(r.interlocked()),z(r.evseKw()),z(r.highPowerCordKw()),n(r.voltage(),600),i(r.phases(),3));}

  public record OtherRequest(Double area,Double wattsPerM2,Double tableDemand,Double specialKw,Double heatKw,Double acKw,Boolean interlocked,Double motorKw,Double evseKw,Double voltage,Integer phases,String occupancy){}
  @PostMapping("/other") public Map<String,Object> other(@RequestBody OtherRequest r){return service.other(n(r.area(),0),n(r.wattsPerM2(),0),n(r.tableDemand(),1),z(r.specialKw()),z(r.heatKw()),z(r.acKw()),b(r.interlocked()),z(r.motorKw()),z(r.evseKw()),n(r.voltage(),208),i(r.phases(),3),r.occupancy());}

  private static double z(Double v){return v==null?0:v;}
  private static double n(Double v,double d){return v==null?d:v;}
  private static int i(Integer v,int d){return v==null?d:v;}
  private static boolean b(Boolean v){return v!=null&&v;}
}
