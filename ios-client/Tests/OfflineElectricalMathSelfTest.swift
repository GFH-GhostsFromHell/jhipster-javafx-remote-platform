import Foundation

@main struct OfflineElectricalMathSelfTest {
    static func expect(_ condition: @autoclosure () -> Bool, _ reason: String) {
        if !condition() { fatalError("Offline arithmetic regression failed: " + reason) }
    }
    static func almost(_ actual: Double?, _ expected: Double, _ reason: String) {
        expect(actual != nil && abs(actual! - expected) < 0.015, reason)
    }
    static func main() {
        // Independent 3-phase and 1-phase formulas, not a code-table lookup.
        almost(OfflineElectricalMath.transformerCurrent(kva: 75,voltage: 600,phases: 3),
               72.1688,"3 phase primary current")
        almost(OfflineElectricalMath.transformerCurrent(kva: 75,voltage: 208,phases: 3),
               208.179,"3 phase secondary current")
        almost(OfflineElectricalMath.transformerCurrent(kva: 10,voltage: 240,phases: 1),
               41.6667,"single phase current")
        expect(OfflineElectricalMath.transformerCurrent(kva: 75,voltage: 600,phases: 2) == nil,
               "invalid phase count")
        expect(OfflineElectricalMath.transformerCurrent(kva: .infinity,voltage: 600,phases: 3) == nil,
               "infinite kVA")
        expect(OfflineElectricalMath.transformerCurrent(kva: 75,voltage: 0,phases: 3) == nil,
               "zero voltage")

        // User-provided Table 2/5A/5C example. A 90°C insulation rating does
        // NOT authorize selecting the 90°C column for 60°C terminations.
        almost(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 60,
               insulationC: 90,ambientFactor: .58,groupingFactor: .8),
               25.52,"60°C termination arithmetic")
        almost(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 75,
               insulationC: 90,ambientFactor: .75,groupingFactor: .8),
               33,"75°C termination arithmetic")
        expect(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 75,
               insulationC: 60,ambientFactor: .75,groupingFactor: .8) == nil,
               "insulation cannot be rated below selected terminal")
        expect(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 65,
               insulationC: 90,ambientFactor: .75,groupingFactor: .8) == nil,
               "invalid termination rating")
        expect(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 60,
               insulationC: 90,ambientFactor: .nan,groupingFactor: .8) == nil,
               "NaN ambient factor")
        expect(OfflineElectricalMath.correctedAmpacity(base: 55,terminationC: 60,
               insulationC: 90,ambientFactor: 1.25,groupingFactor: .8) == nil,
               "impossible correction factor above one")
        expect(OfflineElectricalMath.correctedAmpacity(base: 0,terminationC: 60,
               insulationC: 90,ambientFactor: .58,groupingFactor: .8) == nil,
               "zero base ampacity")
        print("iOS native electrical arithmetic tests passed (12 assertions).")
    }
}
