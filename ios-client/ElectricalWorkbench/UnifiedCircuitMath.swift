import Foundation

/// Native offline circuit assessment. Arithmetic only: no licensed CEC tables,
/// approved conductor sizes, equipment-specific OCPD selection or AC-impedance model.
enum UnifiedCircuitMath {
    struct Input: Equatable {
        let loadA: Double
        let designMultiplier: Double
        let voltageV: Double
        let phases: Int
        let oneWayLengthM: Double
        let circularMils: Double
        let kOhmCircularMilPerFoot: Double
        let terminalColumnAmpacityA: Double
        let terminalC: Int
        let insulationC: Int
        let ambientFactor: Double
        let groupingFactor: Double
        let selectedOcpdA: Double
        let maxVoltageDropPercent: Double
    }

    struct Result {
        let designCurrentA: Double
        let adjustedAmpacityA: Double
        let conductorMarginA: Double
        let voltageDropV: Double
        let voltageDropPercent: Double
        let allowableDropV: Double
        let minimumCircularMilsForDrop: Double
        let maximumOneWayLengthM: Double
        let ampacityArithmeticFits: Bool
        let voltageDropArithmeticFits: Bool
        let protectionScreenFits: Bool
        let codeComplianceVerified = false
    }

    static func calculate(_ input: Input) -> Result? {
        let positive = [input.loadA, input.designMultiplier, input.voltageV,
                        input.oneWayLengthM, input.circularMils,
                        input.kOhmCircularMilPerFoot, input.terminalColumnAmpacityA,
                        input.selectedOcpdA, input.maxVoltageDropPercent]
        guard positive.allSatisfy({ $0.isFinite && $0 > 0 }),
              input.designMultiplier == 1 || input.designMultiplier == 1.25,
              input.phases == 1 || input.phases == 3,
              input.maxVoltageDropPercent < 100,
              let ampacity = OfflineElectricalMath.correctedAmpacity(
                base: input.terminalColumnAmpacityA,
                terminationC: input.terminalC,
                insulationC: input.insulationC,
                ambientFactor: input.ambientFactor,
                groupingFactor: input.groupingFactor) else { return nil }

        let design = input.loadA * input.designMultiplier
        let metresToFeet = 3.280839895013123
        let feet = input.oneWayLengthM * metresToFeet
        let phaseFactor = input.phases == 3 ? sqrt(3.0) : 2.0
        let drop = phaseFactor * input.kOhmCircularMilPerFoot * input.loadA
                   * feet / input.circularMils
        let limit = input.voltageV * input.maxVoltageDropPercent / 100
        let minArea = phaseFactor * input.kOhmCircularMilPerFoot * input.loadA
                      * feet / limit
        let maxLength = (limit * input.circularMils /
                        (phaseFactor * input.kOhmCircularMilPerFoot * input.loadA))
                        / metresToFeet

        guard [design, ampacity, drop, limit, minArea, maxLength]
                .allSatisfy({ $0.isFinite && $0 > 0 }) else { return nil }

        return Result(
            designCurrentA: design,
            adjustedAmpacityA: ampacity,
            conductorMarginA: ampacity - design,
            voltageDropV: drop,
            voltageDropPercent: drop / input.voltageV * 100,
            allowableDropV: limit,
            minimumCircularMilsForDrop: minArea,
            maximumOneWayLengthM: maxLength,
            ampacityArithmeticFits: design <= ampacity + 1e-9,
            voltageDropArithmeticFits: drop <= limit + 1e-9,
            protectionScreenFits: input.selectedOcpdA + 1e-9 >= design
                && input.selectedOcpdA <= ampacity + 1e-9
        )
    }
}
