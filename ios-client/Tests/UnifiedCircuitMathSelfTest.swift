import Foundation

@main struct UnifiedCircuitMathSelfTest {
    static func expect(_ condition: @autoclosure () -> Bool, _ detail: String) {
        if !condition() { fatalError("iOS unified circuit regression: " + detail) }
    }
    static func near(_ actual: Double, _ target: Double, tolerance: Double = 0.015) {
        expect(actual.isFinite && abs(actual - target) <= tolerance,
               "Expected \(target), received \(actual)")
    }
    static func input(
        loadA: Double = 40, phases: Int = 3,
        terminalC: Int = 75, insulationC: Int = 90,
        tableAmpacity: Double = 85, ambient: Double = 1, grouping: Double = 1,
        circularMils: Double = 66360, device: Double = 50,
        run: Double = 40
    ) -> UnifiedCircuitMath.Input {
        UnifiedCircuitMath.Input(
            loadA: loadA, designMultiplier: 1.25, voltageV: 600,
            phases: phases, oneWayLengthM: run, circularMils: circularMils,
            kOhmCircularMilPerFoot: 12.9, terminalColumnAmpacityA: tableAmpacity,
            terminalC: terminalC, insulationC: insulationC,
            ambientFactor: ambient, groupingFactor: grouping,
            selectedOcpdA: device, maxVoltageDropPercent: 3
        )
    }

    static func main() {
        let base = UnifiedCircuitMath.calculate(input())
        expect(base != nil, "3-phase valid example rejected")
        near(base!.designCurrentA, 50)
        near(base!.adjustedAmpacityA, 85)
        near(base!.voltageDropV, 1.76746, tolerance: 0.0001)
        near(base!.voltageDropPercent, 0.294576, tolerance: 0.0001)
        near(base!.minimumCircularMilsForDrop, 6516.03)
        near(base!.maximumOneWayLengthM, 407.365)
        expect(base!.ampacityArithmeticFits, "base ampacity")
        expect(base!.voltageDropArithmeticFits, "base voltage drop")
        expect(base!.protectionScreenFits, "base device screen")
        expect(!base!.codeComplianceVerified, "must never claim CEC approval")

        let single = UnifiedCircuitMath.calculate(input(phases: 1))
        expect(single != nil, "single phase must be permitted")
        near(single!.voltageDropV, 2.04096, tolerance: 0.02)
        expect(single!.voltageDropV > base!.voltageDropV,
               "1ph two-conductor factor should exceed sqrt(3)")

        let hot = UnifiedCircuitMath.calculate(input(terminalC: 60,
            tableAmpacity: 55, ambient: 0.58, grouping: 0.8))
        expect(hot != nil, "hot installation should produce a failing result")
        near(hot!.adjustedAmpacityA, 25.52)
        expect(!hot!.ampacityArithmeticFits, "derated wire must fail")
        expect(!hot!.protectionScreenFits, "device cannot pass failed ampacity")

        let small = UnifiedCircuitMath.calculate(input(circularMils: 4000))
        expect(small != nil && !small!.voltageDropArithmeticFits, "small area fails VD")
        expect(UnifiedCircuitMath.calculate(input(device: 100))?.protectionScreenFits == false,
               "larger device cannot be auto-approved")
        expect(UnifiedCircuitMath.calculate(input(run: 80)) != nil,
               "doubled run must remain calculable")
        near(UnifiedCircuitMath.calculate(input(run: 80))!.voltageDropV,
             base!.voltageDropV * 2, tolerance: 0.0001)

        expect(UnifiedCircuitMath.calculate(input(loadA: 0)) == nil, "zero current")
        expect(UnifiedCircuitMath.calculate(input(loadA: .nan)) == nil, "NaN current")
        expect(UnifiedCircuitMath.calculate(input(phases: 2)) == nil, "invalid phases")
        expect(UnifiedCircuitMath.calculate(input(terminalC: 90,insulationC: 60)) == nil,
               "insulation lower than terminal")
        expect(UnifiedCircuitMath.calculate(input(ambient: 1.2)) == nil,
               "correction factors above 1 need review")
        expect(UnifiedCircuitMath.calculate(input(circularMils: .infinity)) == nil,
               "infinite conductor area")
        print("iOS unified circuit sizing self-tests passed: parity, inputs and fail-safe screens.")
    }
}
