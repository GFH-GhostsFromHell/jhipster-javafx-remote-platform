import SwiftUI
import Foundation

/// Field-first iOS circuit review. Works offline, requires user-verified CEC data.
struct UnifiedCircuitSizingView: View {
    @State private var loadA = "40"
    @State private var voltageV = "600"
    @State private var phases = 3
    @State private var lengthM = "40"
    @State private var deviceA = "50"
    @State private var multiplier = 1.25
    @State private var conductor = "Selected conductor"
    @State private var table = "Table 2"
    @State private var terminal = 75
    @State private var insulation = 90
    @State private var baseAmpacity = "85"
    @State private var ambient = "1.00"
    @State private var grouping = "1.00"
    @State private var circularMils = "66360"
    @State private var kFactor = "12.9"
    @State private var vdTarget = "3"
    @State private var showVDInputs = false
    @State private var output = "Replace sample entries with verified circuit details."

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("CIRCUIT + CONDUCTOR REVIEW")
                    .font(.caption.bold())
                    .foregroundStyle(WorkbenchStyle.cyan)
                Text("One offline worksheet: required current, corrected ampacity, estimated voltage drop, maximum one-way run and a conservative OCPD screen.")
                    .font(.subheadline)
                    .foregroundStyle(WorkbenchStyle.muted)

                VStack(alignment: .leading, spacing: 12) {
                    Text("1 · Circuit").font(.headline)
                    entry("Actual load current (A)", value: $loadA)
                    entry("System voltage (V)", value: $voltageV)
                    Picker("Circuit type", selection: $phases) {
                        Text("Single-phase two-wire").tag(1)
                        Text("Balanced three-phase").tag(3)
                    }.pickerStyle(.menu)
                    entry("One-way conductor length (m)", value: $lengthM)
                    entry("Selected breaker / fuse (A)", value: $deviceA)
                    Picker("General load-sizing treatment", selection: $multiplier) {
                        Text("100% (non-continuous / approved 100% equipment)").tag(1.0)
                        Text("125% (80%-rated equipment)").tag(1.25)
                    }.pickerStyle(.menu)
                }
                .circuitReviewCard()

                VStack(alignment: .leading, spacing: 12) {
                    Text("2 · Conductor and site conditions").font(.headline)
                    Text("Use the actual equipment TERMINATION-temperature ampacity column, not the conductor's higher insulation column.")
                        .foregroundStyle(.orange)
                        .font(.footnote)
                    entry("Selected conductor / trade size", value: $conductor, numeric: false)
                    Picker("Applicable CEC table", selection: $table) {
                        ForEach(["Table 1", "Table 2", "Table 3", "Table 4"], id: \.self) { value in
                            Text(value).tag(value)
                        }
                    }.pickerStyle(.menu)
                    Picker("Verified termination temperature", selection: $terminal) {
                        ForEach([60,75,90], id: \.self) { value in
                            Text("\(value)°C").tag(value)
                        }
                    }.pickerStyle(.menu)
                    Picker("Insulation temperature", selection: $insulation) {
                        ForEach([60,75,90], id: \.self) { value in
                            Text("\(value)°C").tag(value)
                        }
                    }.pickerStyle(.menu)
                    entry("Ampacity from termination column (A)", value: $baseAmpacity)
                    entry("Verified ambient factor (Table 5A)", value: $ambient)
                    entry("Verified grouping factor (Table 5C)", value: $grouping)
                    Button(showVDInputs ? "Hide voltage-drop inputs" : "Edit voltage-drop inputs") {
                        showVDInputs.toggle()
                    }
                    .buttonStyle(.bordered)
                    if showVDInputs {
                        entry("Conductor area (circular mils)", value: $circularMils)
                        entry("Resistance K factor (Ω·cmil/ft)", value: $kFactor)
                        entry("User-selected voltage-drop target (%)", value: $vdTarget)
                    } else {
                        Text("VD uses sample area \(circularMils) cmil, K \(kFactor), target \(vdTarget)%. Expand to confirm.")
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                }
                .circuitReviewCard()

                Button {
                    calculate()
                } label: {
                    Label("Review circuit", systemImage: "function")
                        .frame(maxWidth: .infinity, minHeight: 46)
                }
                .buttonStyle(.borderedProminent)
                .tint(WorkbenchStyle.cyan)
                .foregroundStyle(WorkbenchStyle.background)

                VStack(alignment: .leading, spacing: 10) {
                    Text("3 · Worked result").font(.headline)
                    Text(output)
                        .font(.subheadline.monospacedDigit())
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                    ShareLink(item: "Electrical Workbench · Circuit Review\n" + output) {
                        Label("Share worksheet", systemImage: "square.and.arrow.up")
                    }
                    .font(.subheadline)
                }
                .circuitReviewCard()

                Text("NOT CODE VERIFIED. Verify official CEC 2024 / Ontario tables and amendments, actual termination marks, insulation, installation method, neutral counting, current and OCPD rules. The K-factor drop model is approximate and excludes upstream feeder drop; motor and transformer exceptions require separate review. No conductor size is automatically approved.")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
            .padding(15)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Circuit sizing")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func entry(_ label: String, value: Binding<String>, numeric: Bool = true) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.subheadline.weight(.medium))
            TextField("Enter " + label.lowercased(), text: value)
                .keyboardType(numeric ? .decimalPad : .default)
                .padding(11)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                .accessibilityLabel(label)
        }
    }

    private func finite(_ value: String) -> Double? {
        guard let number = Double(value.trimmingCharacters(in: .whitespaces)),
              number.isFinite else { return nil }
        return number
    }

    private func fmt(_ number: Double) -> String {
        String(format: "%.2f", number)
    }

    private func calculate() {
        guard let load = finite(loadA), let volts = finite(voltageV),
              let distance = finite(lengthM), let breaker = finite(deviceA),
              let base = finite(baseAmpacity), let ambientFactor = finite(ambient),
              let groupingFactor = finite(grouping), let area = finite(circularMils),
              let k = finite(kFactor), let target = finite(vdTarget) else {
            output = "Enter finite numeric measurements. Sample values are not verified Code data."
            return
        }
        let input = UnifiedCircuitMath.Input(
            loadA: load, designMultiplier: multiplier, voltageV: volts,
            phases: phases, oneWayLengthM: distance,
            circularMils: area, kOhmCircularMilPerFoot: k,
            terminalColumnAmpacityA: base, terminalC: terminal,
            insulationC: insulation, ambientFactor: ambientFactor,
            groupingFactor: groupingFactor, selectedOcpdA: breaker,
            maxVoltageDropPercent: target
        )
        guard let result = UnifiedCircuitMath.calculate(input) else {
            output = "Calculation unavailable. Check positive values, 60/75/90°C terminal and insulation compatibility, correction factors ≤1, and a VD target below 100%."
            return
        }
        output = """
        \(conductor) · \(table), \(terminal)°C terminations

        1 · CURRENT / AMPACITY
        Design = \(fmt(load)) × \(fmt(multiplier)) = \(fmt(result.designCurrentA)) A
        Adjusted ampacity = \(fmt(base)) × \(fmt(ambientFactor)) × \(fmt(groupingFactor)) = \(fmt(result.adjustedAmpacityA)) A
        Margin = \(fmt(result.conductorMarginA)) A
        Ampacity screen: \(result.ampacityArithmeticFits ? "WITHIN ENTERED LIMIT" : "FAIL")

        2 · VOLTAGE DROP
        Estimated drop = \(fmt(result.voltageDropV)) V (\(fmt(result.voltageDropPercent))%)
        Entered target = \(fmt(result.allowableDropV)) V (\(fmt(target))%)
        VD screen: \(result.voltageDropArithmeticFits ? "WITHIN ENTERED TARGET" : "EXCEEDS TARGET")
        Minimum conductor area for entered VD target = \(fmt(result.minimumCircularMilsForDrop)) circular mils
        Maximum one-way run at selected area = \(fmt(result.maximumOneWayLengthM)) m

        3 · GENERAL-CIRCUIT DEVICE SCREEN
        Entered breaker/fuse = \(fmt(breaker)) A
        Conservative screen: \(result.protectionScreenFits ? "WITHIN INPUT BOUNDS" : "REVIEW DEVICE / LOAD / CONDUCTOR")

        NOT CODE VERIFIED. No automatic conductor/OCPD approval. Confirm verified tables, terminal markings and actual wiring method. Voltage drop is a resistive K-factor estimate.
        """
    }
}

private struct CircuitReviewCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
    }
}

private extension View {
    func circuitReviewCard() -> some View { modifier(CircuitReviewCard()) }
}
