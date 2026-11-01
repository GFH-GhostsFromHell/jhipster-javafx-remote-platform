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

  @GetMapping("/editions")
  public List<Map<String,Object>> editions() {
    return calculators.editions();
  }

  @GetMapping("/catalog")
  public Map<String,Object> catalog(Principal principal) {
    String tier;
    try { tier = accounts.tier(principal.getName()); }
    catch (Exception ex) { tier = "ADMIN"; }
    return calculators.catalog(tier);
  }

  public record TransformerRequest(
    Double kva,
    Double primaryVoltage,
    Double secondaryVoltage,
    Integer phases,
    Double designFactor,
    String edition
  ) {}

  @PostMapping("/transformer")
  public Map<String,Object> transformer(@RequestBody TransformerRequest request) {
    double kva = request.kva() == null ? 0 : request.kva();
    double primary = request.primaryVoltage() == null ? 0 : request.primaryVoltage();
    double secondary = request.secondaryVoltage() == null ? 0 : request.secondaryVoltage();
    int phases = request.phases() == null ? 3 : request.phases();
    double factor = request.designFactor() == null ? 1.25 : request.designFactor();
    String edition = request.edition() == null || request.edition().isBlank()
      ? ElectricalCalculatorService.DEFAULT_EDITION
      : request.edition();
    return calculators.transformer(kva, primary, secondary, phases, factor, edition);
  }
}
