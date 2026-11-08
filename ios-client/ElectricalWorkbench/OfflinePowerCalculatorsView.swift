import SwiftUI
import Foundation

private enum OfflineCalculation: String, CaseIterable, Identifiable {
    case transformer = "Transformer currents"
    case motor = "Motor branch worksheet"
    case conductorAmpacity = "Conductor termination / ampacity"
    case voltageDrop = "Conductor voltage drop"
    case busbar = "Busbar / straps"
    case capacitor = "Capacitor bank"
    case powerFactor = "Power factor correction"
    case fault = "Transformer fault current"
    case phasePower = "Three-phase power"
    case analog = "4–20 mA instrument scaling"
    case welder = "Welder duty-cycle worksheet"
    case refrigerant = "Refrigeration compressor"
    case expansion = "PVC thermal expansion"
    var id: String { rawValue }
}

private struct CalcInput: Identifiable {
    let id: String
    let label: String
    let example: String
}

private extension OfflineCalculation {
    var inputs: [CalcInput] {
        func f(_ id: String, _ label: String, _ example: String) -> CalcInput {
            CalcInput(id: id, label: label, example: example)
        }
        switch self {
        case .transformer:
            return [f("kva","Rating (kVA)","75"), f("pri","Primary voltage (V)","600"),
                    f("sec","Secondary voltage (V)","208"),f("phases","Phases: 1 or 3","3"),
                    f("primaryPercent","Primary OCPD factor (entered %)","125"),
                    f("secondaryPercent","Secondary OCPD factor (entered %)","125")]
        case .motor:
            return [f("fla","Motor full-load current (A)","34"),f("conductorFactor","Conductor sizing factor (%)","125"),
                    f("ocpdFactor","Selected device maximum multiplier (%)","175"),
                    f("overloadFactor","Selected overload setting (%)","115")]
        case .conductorAmpacity:
            return [f("load","Required design current (A)","30"),
                    f("base","Ampacity from TERMINATION temperature column (A)","55"),
                    f("terminalTemp","Verified equipment termination rating (°C)","60"),
                    f("insulationTemp","Wire insulation rating (°C)","90"),
                    f("ambientFactor","Verified ambient factor from Table 5A","0.58"),
                    f("groupingFactor","Verified conductor grouping factor from Table 5C","0.80")]
        case .voltageDrop:
            return [f("amps","Load current (A)","40"),f("length","One-way route length (m)","50"),
                    f("k","Conductor K factor","12.9"),f("cm","Conductor area (circular mils)","133100"),
                    f("phases","Phases: 1 or 3","3"),f("system","System voltage (V)","600")]
        case .busbar:
            return [f("current","Required current (A)","3000"),f("width","Bar width (mm)","50"),
                    f("thickness","Bar thickness (mm)","6"), f("k","Chosen material method K (A/in²)","1000")]
        case .capacitor:
            return [f("kvar","Capacitor bank (kvar)","25"),f("voltage","Supply voltage (V)","600"),
                    f("phases","Phases: 1 or 3","3")]
        case .powerFactor:
            return [f("kw","Real power (kW)","100"),f("initial","Existing PF (0–1)","0.75"),
                    f("target","Target PF (0–1)","0.95"),f("voltage","Voltage (V)","600")]
        case .fault:
            return [f("kva","Transformer rating (kVA)","75"),f("voltage","Secondary voltage (V)","208"),
                    f("phases","Phases: 1 or 3","3"),f("impedance","Transformer impedance (%)","3"),
                    f("upstream","Upstream source fault current (A; 0 = ignore)","0")]
        case .phasePower:
            return [f("voltage","Line-to-line voltage (V)","600"),f("amps","Line current (A)","100"),
                    f("pf","Power factor (0–1)","0.85")]
        case .analog:
            return [f("minCurrent","Signal minimum (mA)","4"),f("maxCurrent","Signal maximum (mA)","20"),
                    f("minimum","Engineering range minimum","0"),f("maximum","Engineering range maximum","1800"),
                    f("setpoint","Required engineering value","1200")]
        case .welder:
            return [f("primary","Rated primary current (A)","50"),f("dutyFactor","CEC/reference duty multiplier (0–1)","0.71"),
                    f("ocpdFactor","Selected OCPD multiplier (%)","200")]
        case .refrigerant:
            return [f("rla","Rated load current RLA (A)","28"),f("mcaFactor","MCA multiplier (%)","125"),
                    f("mocp","Nameplate max OCPD (A)","45")]
        case .expansion:
            return [f("length","PVC straight run (m)","45"),f("deltaT","Maximum temperature change (°C)","50"),
                    f("coefficient","Manufacturer linear expansion (mm/m/°C)","0.054"),
                    f("jointTravel","Usable expansion joint travel (mm)","50")]
        }
    }

    var referenceNote: String {
        switch self {
        case .transformer: "Use applicable CEC 2024 transformer protection rules/tables, actual device and transformer ratings. Percent fields are user-provided calculation assumptions, not code limits."
        case .motor: "Motor FLC, conductor, overload, starter and OCPD rules differ by device, motor type and duty. Select the correct CEC 2024 factor; this calculation does not choose a legal device."
        case .conductorAmpacity: "Rule 4-006 worksheet only. Enter ampacity from the verified equipment TERMINATION temperature column of the applicable CEC table (1–4), plus relevant Table 5A/5C factors. Values are user-supplied; this does not approve conductor size, rating, circuit protection, or installation."
        case .voltageDrop: "Basic conductor K-factor worksheet. Verify AC impedance, corrected ampacity, actual terminals and CEC Table D3 conditions before a field decision."
        case .busbar: "Illustrative current-density method only. Use engineered bar construction, spacing and equipment approvals for actual busbar sizing."
        case .capacitor: "Capacitor current is not the final conductor or fuse rating. Check manufacturer instructions and applicable Code protection provisions."
        case .powerFactor: "Ideal correction; actual system harmonics, voltage and capacitor bank steps require engineering verification."
        case .fault: "Simple symmetrical transformer-terminal estimate; this is NOT a protective-device interrupting rating or arc-flash/coordination study."
        case .phasePower: "Balanced sinusoidal three-phase power calculation."
        case .analog: "Uses configured engineering span, not actual loaded motor speed."
        case .welder: "The multiplier must come from the correct CEC 2024 welder type and duty-cycle table. Do not assume that all welders use the same factor."
        case .refrigerant: "Nameplate MCA/MOCP and unit instructions take precedence. This comparison does not approve the OCPD."
        case .expansion: "Manufacturer coefficient, fitting travel, ambient range, anchor locations and applicable CEC installation rule must be verified."
        }
    }
}

private enum CalculationError: Error {
    case bad(String)
    var text: String {
        switch self { case .bad(let message): message }
    }
}

struct OfflinePowerCalculatorsView: View {
    @State private var kind: OfflineCalculation = .transformer
    @State private var values: [String: String] = [:]
    @State private var output = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("FIELD ELECTRICAL CALCULATORS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Quick offline formulas with user-entered parameters. Code-table values are not guessed or embedded.")
                        .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
                    Picker("Calculator", selection: $kind) {
                        ForEach(OfflineCalculation.allCases) { type in
                            Text(type.rawValue).tag(type)
                        }
                    }
                    .pickerStyle(.menu)
                    .onChange(of: kind) { _, _ in
                        values.removeAll()
                        output = ""
                    }
                }
                .powerCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text(kind.rawValue.uppercased())
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    ForEach(kind.inputs) { field in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(field.label).font(.subheadline.weight(.medium))
                            TextField(field.example, text: Binding(
                                get: { values[field.id] ?? "" },
                                set: { values[field.id] = $0 }
                            ))
                            .keyboardType(.decimalPad)
                            .font(.body.monospacedDigit())
                            .padding(11)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                            .accessibilityLabel(field.label)
                        }
                    }
                    Text("Suggested values are placeholders; enter your job's measured/nameplate values.")
                        .font(.caption)
                        .foregroundStyle(WorkbenchStyle.muted)
                    Button {
                        do { output = try calculate() }
                        catch let error as CalculationError { output = error.text }
                        catch { output = "Check the input measurements." }
                    } label: {
                        Label("Calculate", systemImage: "function")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(WorkbenchStyle.cyan)
                    .foregroundStyle(WorkbenchStyle.background)
                }
                .powerCard()

                if !output.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("WORKED RESULT")
                            .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        Text(output)
                            .font(.subheadline.monospacedDigit())
                            .textSelection(.enabled)
                            .fixedSize(horizontal: false, vertical: true)
                        ShareLink(item: kind.rawValue + "\n" + output + "\n" + kind.referenceNote) {
                            Label("Share result", systemImage: "square.and.arrow.up")
                        }
                        .font(.subheadline)
                    }
                    .powerCard()
                }

                VStack(alignment: .leading, spacing: 10) {
                    Label("VERIFICATION", systemImage: "exclamationmark.shield")
                        .font(.caption.bold())
                        .foregroundStyle(.orange)
                    Text(kind.referenceNote)
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                    NavigationLink {
                        PortalScreen(title: "CEC 2024 worksheets", path: "/calculators.html")
                    } label: {
                        Label("Open the full cloud CEC calculators", systemImage: "arrow.up.right.square")
                    }
                    NavigationLink {
                        PortalScreen(title: "Advanced engineering", path: "/advanced-calculations.html")
                    } label: {
                        Label("Open advanced code & engineering worksheets", systemImage: "bolt")
                    }
                }
                .powerCard()
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Electrical calculators")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func n(_ key: String, positive: Bool = true) throws -> Double {
        guard let text = values[key], let value = Double(text), value.isFinite,
              (!positive || value > 0) else {
            throw CalculationError.bad("Enter a \(positive ? "positive" : "valid") value for \(kind.inputs.first(where: { $0.id == key })?.label ?? key).")
        }
        return value
    }

    private func phase(_ key: String = "phases") throws -> Double {
        let p = try n(key)
        guard p == 1 || p == 3 else { throw CalculationError.bad("Phase count must be 1 or 3.") }
        return p == 3 ? sqrt(3) : 1
    }
    private func format(_ value: Double, unit: String = "") -> String {
        String(format: "%.2f", value) + (unit.isEmpty ? "" : " " + unit)
    }

    private func calculate() throws -> String {
        switch kind {
        case .transformer:
            let kva = try n("kva"), primary = try n("pri"), secondary = try n("sec")
            let scale = try phase()
            guard let a = OfflineElectricalMath.transformerCurrent(kva: kva, voltage: primary,
                                                                phases: scale == 1 ? 1 : 3),
                  let b = OfflineElectricalMath.transformerCurrent(kva: kva, voltage: secondary,
                                                                phases: scale == 1 ? 1 : 3) else {
                throw CalculationError.bad("Transformer inputs exceed the supported finite range.")
            }
            let factorA = try n("primaryPercent") / 100, factorB = try n("secondaryPercent") / 100
            return """
            Formula: I = VA / (\(format(scale)) × V)
            Primary FLA: \(format(a, unit: "A"))
            Secondary FLA: \(format(b, unit: "A"))
            Unrounded primary OCPD worksheet: \(format(a * factorA, unit: "A"))
            Unrounded secondary OCPD worksheet: \(format(b * factorB, unit: "A"))
            Verify the applicable rules and standard device ratings before selecting equipment.
            """
        case .motor:
            let a = try n("fla")
            return """
            Motor FLC: \(format(a, unit: "A"))
            Minimum ampacity worksheet: \(format(a * (try n("conductorFactor")) / 100, unit: "A"))
            Selected OCPD upper limit (UNROUNDED): \(format(a * (try n("ocpdFactor")) / 100, unit: "A"))
            Entered overload setting: \(format(a * (try n("overloadFactor")) / 100, unit: "A"))
            CEC device-specific factors and conductor ampacity still require verification.
            """
        case .conductorAmpacity:
            let load = try n("load")
            let base = try n("base")
            let terminal = try n("terminalTemp"), insulation = try n("insulationTemp")
            guard [60.0,75.0,90.0].contains(terminal),
                  [60.0,75.0,90.0].contains(insulation) else {
                throw CalculationError.bad("Enter a whole-number 60, 75, or 90 °C rating.")
            }
            let ambient = try n("ambientFactor"), grouping = try n("groupingFactor")
            guard let adjusted = OfflineElectricalMath.correctedAmpacity(base: base,
                     terminationC: Int(terminal), insulationC: Int(insulation),
                     ambientFactor: ambient, groupingFactor: grouping) else {
                throw CalculationError.bad("Check termination and insulation (60/75/90°C), base ampacity and correction factors (0–1).")
            }
            return """
            RULE 4-006 CONDUCTOR REVIEW — NOT CODE VERIFIED
            Entered TERMINATION column: \(Int(terminal)) °C
            Insulation temperature: \(Int(insulation)) °C
            Entered table ampacity: \(format(base, unit: "A"))
            Ambient factor: \(format(ambient)); grouping: \(format(grouping))
            Corrected ampacity: \(format(adjusted, unit: "A"))
            Required current: \(format(load, unit: "A"))
            Arithmetic check: \(adjusted >= load ? "WITHIN ENTERED AMPACITY" : "FAIL / OVERRUN")
            Verify actual equipment marking, appropriate Table 1–4 column, Table 5A/5C, neutral count, wiring method, Ontario amendments and OCPD. Values are user-entered and not independently verified.
            """
        case .voltageDrop:
            let factor = try phase() == 1 ? 2 : sqrt(3)
            let ft = try n("length") * 3.28084
            let drop = factor * (try n("k")) * (try n("amps")) * ft / (try n("cm"))
            return "Voltage drop: \(format(drop, unit: "V"))\nSystem drop: \(format(drop / (try n("system")) * 100, unit: "%"))\nFormula: \(format(factor)) × K × I × one-way ft / circular mils."
        case .busbar:
            let amps = try n("current"),w = try n("width"),t = try n("thickness"),k = try n("k")
            let per = (w * t / 645.16) * k
            return "Bar area: \(format(w * t, unit: "mm²"))\nIllustrative single bar capacity: \(format(per, unit: "A"))\nParallel straps required (rounded UP): \(Int(ceil(amps / per)))\nUse engineered de-rating, cooling, material and approvals."
        case .capacitor:
            let factor = try phase()
            let amps = try n("kvar") * 1000 / (factor * (try n("voltage")))
            return "Bank current: \(format(amps, unit: "A"))\nFormula: kvar × 1,000 / (\(format(factor)) × V)."
        case .powerFactor:
            let kw = try n("kw"), old = try n("initial"), target = try n("target")
            guard old < 1 && target <= 1 && target > old else {
                throw CalculationError.bad("PF must be 0–1 and target must exceed the current PF.")
            }
            let kvar = kw * (tan(acos(old)) - tan(acos(target)))
            let amps = kvar * 1000 / (sqrt(3) * (try n("voltage")))
            return "Ideal correction: \(format(kvar, unit: "kvar"))\nApproximate three-phase capacitor current: \(format(amps, unit: "A"))."
        case .fault:
            let kva = try n("kva"), volts = try n("voltage"), z = try n("impedance") / 100
            let scale = try phase()
            let fla = kva * 1000 / (scale * volts)
            let limited = fla / z
            let upstream = try n("upstream", positive: false)
            guard upstream >= 0 else { throw CalculationError.bad("Upstream current cannot be negative.") }
            let combined = upstream > 0 ? 1 / (1 / limited + 1 / upstream) : limited
            return "Transformer FLA: \(format(fla, unit: "A"))\nTransformer-limited fault: \(format(limited, unit: "A"))\nEstimated combined source/transformer fault: \(format(combined, unit: "A"))."
        case .phasePower:
            let v = try n("voltage"), amps = try n("amps"), pf = try n("pf")
            guard pf <= 1 else { throw CalculationError.bad("Power factor cannot exceed 1.") }
            let kva = sqrt(3) * v * amps / 1000
            return "Apparent: \(format(kva, unit: "kVA"))\nReal: \(format(kva * pf, unit: "kW"))\nReactive: \(format(kva * sqrt(1 - pf * pf), unit: "kvar"))."
        case .analog:
            let lo = try n("minCurrent", positive: false), hi = try n("maxCurrent")
            let minValue = try n("minimum", positive: false), maxValue = try n("maximum")
            let setpoint = try n("setpoint", positive: false)
            guard hi > lo, maxValue > minValue, setpoint >= minValue, setpoint <= maxValue else {
                throw CalculationError.bad("Invalid signal range or setpoint outside the engineering span.")
            }
            let mA = lo + (setpoint - minValue) / (maxValue - minValue) * (hi - lo)
            return "Command signal: \(format(mA, unit: "mA"))\nFraction: \(format((setpoint - minValue) / (maxValue - minValue) * 100, unit: "%"))."
        case .welder:
            let a = try n("primary"), duty = try n("dutyFactor")
            guard duty <= 1 else { throw CalculationError.bad("Duty-cycle multiplier must be 0–1.") }
            return "Welder conductor worksheet: \(format(a * duty, unit: "A"))\nEntered OCPD worksheet: \(format(a * (try n("ocpdFactor")) / 100, unit: "A")).\nActual device selection depends on welder classification and CEC protection rules."
        case .refrigerant:
            let rla = try n("rla")
            return "Compressor MCA worksheet: \(format(rla * (try n("mcaFactor")) / 100, unit: "A"))\nNameplate max OCPD entered: \(format(try n("mocp"), unit: "A")).\nCheck nameplate and manufacturer's maximum fuse/breaker data."
        case .expansion:
            let movement = (try n("length")) * (try n("deltaT")) * (try n("coefficient"))
            let travel = try n("jointTravel")
            return "Calculated PVC thermal movement: \(format(movement, unit: "mm"))\nMinimum joint-travel count by simple ratio: \(Int(ceil(movement / travel)))\nFitting placement must follow manufacturer/CEC and actual anchors."
        }
    }
}

private struct PowerCard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func powerCard() -> some View { modifier(PowerCard()) }
}
