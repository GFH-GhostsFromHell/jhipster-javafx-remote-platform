import Foundation

/// Shared offline arithmetic. These helpers do not embed or certify any CEC table
/// values: callers must supply ampacity and correction factors from an authorized
/// CEC 2024/OESC source appropriate to the actual installation.
enum OfflineElectricalMath {
    static func transformerCurrent(kva: Double, voltage: Double, phases: Int) -> Double? {
        guard kva.isFinite, kva > 0, voltage.isFinite, voltage > 0,
              phases == 1 || phases == 3 else { return nil }
        let value = kva * 1000 / ((phases == 3 ? sqrt(3.0) : 1.0) * voltage)
        return value.isFinite && value > 0 ? value : nil
    }

    static func correctedAmpacity(base: Double, terminationC: Int, insulationC: Int,
                                  ambientFactor: Double, groupingFactor: Double) -> Double? {
        guard base.isFinite, base > 0,
              [60,75,90].contains(terminationC),
              [60,75,90].contains(insulationC),
              insulationC >= terminationC,
              ambientFactor.isFinite, ambientFactor > 0, ambientFactor <= 1,
              groupingFactor.isFinite, groupingFactor > 0, groupingFactor <= 1 else {
            return nil
        }
        let result = base * ambientFactor * groupingFactor
        return result.isFinite && result > 0 ? result : nil
    }
}
