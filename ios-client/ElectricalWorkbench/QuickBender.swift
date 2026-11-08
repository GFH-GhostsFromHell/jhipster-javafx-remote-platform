import SwiftUI
import Foundation

/// Fast field calculations. Dimensions are conduit-centreline geometry; shoe marks require verification.
private enum BendMode: String, CaseIterable, Identifiable {
    case offset = "Offset"
    case rolling = "Rolling"
    case stub = "90° stub"
    case back = "Back-to-back 90s"
    case saddle3 = "Three-point saddle"
    case saddle4 = "Four-point saddle"
    case kicked90 = "Kicked 90°"
    var id: String { rawValue }
}

private enum BendUnits: String, CaseIterable, Identifiable {
    case mm = "mm"
    case inches = "in"
    var id: String { rawValue }
    var stock: Double { self == .mm ? 3048 : 120 }
}

private struct PencilMark: Identifiable {
    let id: String
    let distance: Double
    let shoeReference: String
}

private struct BendPlan {
    let marks: [PencilMark]
    let spacing: Double?
    let shrink: Double?
    let detail: String
    let warning: String
}

private enum PlanState {
    case waiting(String)
    case invalid(String)
    case ready(BendPlan)
}

private enum FieldError: Error {
    case invalid(String)
    var message: String {
        switch self {
        case .invalid(let message): message
        }
    }
}

struct QuickBenderView: View {
    @State private var mode: BendMode = .offset
    @State private var units: BendUnits = .mm
    @State private var angle: Double = 30
    @State private var farCritical = false

    @State private var rise = ""
    @State private var roll = ""
    @State private var distance = ""
    @State private var finishedStub = ""
    @State private var deduct = ""
    @State private var width = ""
    @State private var centre = ""
    @State private var saddleMethod = "Traditional"
    @AppStorage("ewb.ios.calibration.deduct.mm") private var savedDeductMm = 0.0
    @AppStorage("ewb.ios.calibration.minMarkGap.mm") private var savedMinMarkGapMm = 0.0
    @AppStorage("ewb.ios.calibration.name") private var savedName = ""
    @AppStorage("ewb.ios.calibration.size") private var savedSize = ""
    @AppStorage("ewb.ios.calibration.material") private var savedMaterial = "EMT"
    @State private var minimumGap = ""
    @State private var showOptions = false

    @FocusState private var focusedInput: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 13) {
                VStack(alignment: .leading, spacing: 5) {
                    Text("MEASURE → MARK → BEND")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(WorkbenchStyle.cyan)
                    Text("Know what to measure first. Get pencil marks instantly.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                }

                section {
                    HStack {
                        Text("Bend").font(.subheadline.weight(.semibold))
                        Spacer(minLength: 4)
                        Picker("Bend", selection: $mode) {
                            ForEach(BendMode.allCases) { bend in
                                Text(bend.rawValue).tag(bend)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    HStack {
                        Text("Units").font(.subheadline.weight(.semibold))
                        Spacer(minLength: 12)
                        Picker("Units", selection: $units) {
                            ForEach(BendUnits.allCases) { unit in
                                Text(unit.rawValue).tag(unit)
                            }
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 180)
                    }
                }

                section {
                    Label("HOW TO MEASURE", systemImage: "ruler")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(WorkbenchStyle.cyan)
                    BendSketch(mode: mode)
                        .frame(height: 114)
                        .accessibilityLabel(sketchDescription)
                    Text(datum)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    ForEach(Array(measureSteps.enumerated()), id: \.offset) { number, line in
                        Text("\(number + 1). \(line)")
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text("Sketch shows job dimensions, not exact shoe alignment marks.")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }

                section {
                    bendInputFields
                }

                section {
                    HStack {
                        Image(systemName: "pencil.line")
                            .foregroundStyle(WorkbenchStyle.cyan)
                        Text("YOUR TAPE MARKS")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                    }
                    switch planState {
                    case .waiting(let message):
                        Text(message)
                            .font(.subheadline)
                            .foregroundStyle(WorkbenchStyle.muted)
                    case .invalid(let message):
                        Label(message, systemImage: "exclamationmark.triangle")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                            .fixedSize(horizontal: false, vertical: true)
                    case .ready(let plan):
                        ForEach(plan.marks) { mark in
                            VStack(alignment: .leading, spacing: 5) {
                                Text("MARK \(mark.id) · \(mark.shoeReference)")
                                    .font(.caption.bold())
                                    .foregroundStyle(WorkbenchStyle.cyan)
                                Text(mark.distance.measurement(units))
                                    .font(.title2.bold().monospacedDigit())
                                    .foregroundStyle(.white)
                                Text(mark.shoeReference.contains("GEOMETRY") ? "Planning coordinate only — DO NOT align a shoe here." : "Measure from the stated datum; verify shoe ARROW/STAR/NOTCH alignment.")
                                    .font(.caption)
                                    .foregroundStyle(WorkbenchStyle.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(11)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))
                        }
                        if let spacing = plan.spacing {
                            summary("Between A & B", spacing.measurement(units))
                        }
                        if let shrink = plan.shrink {
                            summary("Calculated shrink", shrink.measurement(units))
                        }
                        Text(plan.detail)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                        Label(plan.warning, systemImage: "exclamationmark.shield")
                            .font(.caption)
                            .foregroundStyle(.orange)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                section {
                    Button {
                        showOptions.toggle()
                    } label: {
                        HStack {
                            Text(showOptions ? "Hide shoe spacing check" : "More options · shoe spacing")
                            Spacer()
                            Image(systemName: showOptions ? "chevron.up" : "chevron.down")
                        }
                        .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.plain)
                    if showOptions && [.offset, .rolling, .saddle3, .saddle4].contains(mode) {
                        entry("Verified minimum mark spacing (\(units.rawValue))",
                              id: "gap", value: $minimumGap,
                              help: "Use a value confirmed by the shoe maker or a safe, measured test. Blank means NOT VERIFIED.")
                        if savedMinMarkGapMm > 0 {
                            Button {
                                minimumGap = String(format: "%.3f", savedMinMarkGapMm / (units == .mm ? 1 : 25.4))
                            } label: {
                                Text("Use measured spacing · \(savedName) (\(savedSize) \(savedMaterial))")
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    NavigationLink { PortalScreen(title: "3D advanced bender", path: "/bend-instruction-lab.html") } label: {
                        Label("Full 3D multi-bend and detailed shoe directions", systemImage: "cube.transparent")
                            .font(.subheadline)
                    }
                    Text("One straight 10-ft EMT length: \(units.stock.measurement(units)). Marks outside stock or overlapping marks are rejected; optional measured spacing checks are applied when entered. Bend arcs, hook access and springback still require a field check.")
                        .font(.caption)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(WorkbenchStyle.background)
        .navigationTitle("Quick bender")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedInput = nil }
            }
        }
        .onChange(of: units) { oldValue, newValue in
            guard oldValue != newValue else { return }
            let factor: Double = newValue == .mm ? 25.4 : (1 / 25.4)
            for converted in [$rise, $roll, $distance, $finishedStub, $deduct, $minimumGap, $width, $centre] {
                if let value = Double(converted.wrappedValue), value.isFinite {
                    converted.wrappedValue = String(format: "%.3f", value * factor)
                }
            }
        }
    }

    @ViewBuilder private var bendInputFields: some View {
        Text("ENTER JOB DIMENSIONS")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(WorkbenchStyle.cyan)
        ninetyInputs
        riseInputs
    }

    @ViewBuilder private var ninetyInputs: some View {
                    if [.stub, .back, .kicked90].contains(mode) {
                        entry("Finished first stub (\(units.rawValue))", id: "stub", value: $finishedStub,
                              help: "From the original short end using the verified shoe reference.")
                        entry("Actual shoe deduct (\(units.rawValue))", id: "deduct", value: $deduct,
                              help: "Use your exact bender's measured or stamped deduct.")
                        if savedDeductMm > 0 {
                            Button {
                                let converter = units == .mm ? 1.0 : 25.4
                                deduct = String(format: "%.3f", savedDeductMm / converter)
                            } label: {
                                Text("Use measured deduct · \(savedName)")
                                    .lineLimit(2)
                            }
                            .font(.caption.weight(.semibold))
                        }
                        NavigationLink("Measure / edit shoe calibration") {
                            BenderCalibrationView()
                        }
                        .font(.caption)
                        if mode == .back {
                            entry("Back-of-first-90 to second STAR (\(units.rawValue))",
                                  id: "width", value: $width,
                                  help: "Measure AFTER bending the first 90. Do not use original tape zero.")
                        }
                    }
    }

    @ViewBuilder private var riseInputs: some View {
                    if [.offset, .rolling, .saddle3, .saddle4, .kicked90].contains(mode) {
                        entry(mode == .kicked90 ? "Kick rise (\(units.rawValue))" : "Centreline rise (\(units.rawValue))",
                              id: "rise", value: $rise,
                              help: "Measure perpendicular to the original conduit run, including clearance.")
                        if mode == .rolling {
                            entry("Sideways roll (\(units.rawValue))", id: "roll", value: $roll,
                                  help: "Horizontal shift, NOT diagonal distance.")
                        }
                        if [.offset, .rolling, .saddle4].contains(mode) {
                            entry(mode == .saddle4 ? "Near obstacle face from tape 0 (\(units.rawValue))" : "Reference distance (\(units.rawValue))",
                                  id: "distance", value: $distance,
                                  help: "Measure along the original straight conduit run from tape zero.")
                        }
                        if mode == .saddle3 {
                            entry("Obstruction CENTRE from tape 0 (\(units.rawValue))",
                                  id: "centre", value: $centre,
                                  help: "Measure to the centre, not the near edge of the obstruction.")
                            Picker("Marking method", selection: $saddleMethod) {
                                Text("Traditional notch + arrow").tag("Traditional")
                                Text("Geometry only").tag("Geometry")
                            }
                            .pickerStyle(.menu)
                        }
                        if mode == .saddle4 {
                            entry("Obstruction width (\(units.rawValue))", id: "width", value: $width,
                                  help: "Measure along the original run, including required clearance.")
                        }
                        Picker(mode == .saddle3 ? "Centre bend angle" : "Angle of each bend", selection: $angle) {
                            ForEach([10.0, 15.0, 22.5, 30.0, 45.0, 60.0], id: \.self) { a in
                                Text("\(a.formatted())°").tag(a)
                            }
                        }
                        .font(.subheadline)
                        .pickerStyle(.menu)
                        if [.offset, .rolling].contains(mode) {
                            Picker("Critical bend", selection: $farCritical) {
                                Text("Near bend").tag(false)
                                Text("Far bend").tag(true)
                            }
                            .font(.subheadline)
                            .pickerStyle(.segmented)
                        }
                    }
    }

    @ViewBuilder private func section<Content: View>(
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 15))
        .overlay(RoundedRectangle(cornerRadius: 15).stroke(WorkbenchStyle.border, lineWidth: 1))
    }

    private func entry(_ label: String, id: String,
                       value: Binding<String>, help: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            TextField("Enter measurement", text: value)
                .keyboardType(.decimalPad)
                .textContentType(.none)
                .focused($focusedInput, equals: id)
                .font(.body.monospacedDigit())
                .padding(.horizontal, 11)
                .frame(minHeight: 46)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                .accessibilityLabel(label)
            Text(help)
                .font(.caption)
                .foregroundStyle(WorkbenchStyle.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func summary(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title).foregroundStyle(WorkbenchStyle.muted)
            Spacer(minLength: 8)
            Text(value).foregroundStyle(.white).bold().monospacedDigit()
        }
        .font(.subheadline)
    }

    private var datum: String {
        switch mode {
        case .stub, .back, .kicked90: "TAPE 0 = the original short end of the first 90°."
        case .saddle3: "TAPE 0 → CENTRE of the obstruction."
        case .saddle4: "TAPE 0 → NEAR FACE of the obstruction."
        case .offset, .rolling: "TAPE 0 = one square end of the straight conduit."
        }
    }

    private var measureSteps: [String] {
        switch mode {
        case .stub:
            return ["Measure the required finished stub height.",
                    "Measure your shoe deduct, then subtract it from the stub height.",
                    "Align the shoe ARROW at that mark; the hook faces the short end for matching tools."]
        case .offset:
            return ["Measure along the run from tape zero to your critical-bend reference.",
                    "Measure rise UP between original and final centreline, including clearance.",
                    "Select bend angle; A and B are both measured from the same tape-zero end."]
        case .rolling:
            return ["Measure the run distance from tape zero to the reference line.",
                    "Measure rise UP and sideways roll separately — not the diagonal.",
                    "The app combines them into the true offset; bend in the required rolled plane."]
        case .back:
            return ["Measure first finished stub and actual deduct from the short end.",
                    "Bend the first 90 at its ARROW, then measure FROM ITS BACK for the second STAR.",
                    "The second mark has a different reference, not the original tape end."]
        case .saddle3:
            return ["Measure from tape zero to the CENTRE of the obstruction.",
                    "Measure the perpendicular clearance rise.",
                    "Traditional centre NOTCH and outer ARROWS are shoe-method-specific."]
        case .saddle4:
            return ["Measure distance to the NEAR obstacle face, obstruction WIDTH, and clearance RISE.",
                    "Four bend centres are geometric layout points, not verified shoe alignment marks.",
                    "Use the 3D advanced bender and calibrate your shoe before fabrication."]
        case .kicked90:
            return ["Measure first finished stub from the short end and use actual deduct.",
                    "Measure kick rise perpendicular to the first bent run.",
                    "Kick rise alone cannot locate a physical kick mark; choose a measured kick datum."]
        }
    }

    private var sketchDescription: String {
        switch mode {
        case .stub: "An L-shaped conduit; tape zero is at the end of the short upright stub."
        case .offset: "Two equal bends lift the conduit from the starting line to a higher parallel line."
        case .rolling: "A two-bend offset with vertical rise and sideways horizontal roll."
        case .back: "Two opposing 90-degree bends; the second is marked from the back of the first."
        case .saddle3: "Three bend saddle over an obstacle, with its centre above the obstacle."
        case .saddle4: "Four bends lift a conduit over a wide obstacle and return it to the original plane."
        case .kicked90: "A 90-degree stub followed by a separate kick; the kick needs a distinct datum."
        }
    }

    private var planState: PlanState {
        let needed: [String]
        switch mode {
        case .stub: needed = [finishedStub, deduct]
        case .back: needed = [finishedStub, deduct, width]
        case .kicked90: needed = [finishedStub, deduct, rise]
        case .offset: needed = [rise, distance]
        case .rolling: needed = [rise, roll, distance]
        case .saddle3: needed = [rise, centre]
        case .saddle4: needed = [rise, distance, width]
        }
        guard needed.allSatisfy({ !$0.trimmingCharacters(in: .whitespaces).isEmpty }) else {
            return .waiting("Enter the measured job dimensions above. Results update as you type.")
        }
        do {
            return .ready(try calculatePlan())
        } catch let error as FieldError {
            return .invalid(error.message)
        } catch {
            return .invalid("Check your measurements.")
        }
    }

    private func positive(_ input: String, label: String) throws -> Double {
        guard let value = Double(input), value.isFinite, value > 0 else {
            throw FieldError.invalid("Enter a positive \(label).")
        }
        return value
    }

    private func calculatePlan() throws -> BendPlan {
        let stock = units.stock
        let shoeWarning = "Marks fit within 10 ft. Confirm shoe radius, hook clearance and springback on your actual bender."
        if [.stub, .back, .kicked90].contains(mode) {
            let required = try positive(finishedStub, label: "finished stub height")
            let takeup = try positive(deduct, label: "measured shoe deduct")
            guard required > takeup else {
                throw FieldError.invalid("Stub height must exceed your verified deduct.")
            }
            let mark = required - takeup
            guard mark <= stock && required <= stock else {
                throw FieldError.invalid("CALCULATION NOT POSSIBLE on one 10-ft EMT length.")
            }
            var extra = "Finished stub − verified deduct = ARROW distance. Check the finished dimension after bending."
            if mode == .back {
                let separation = try positive(width, label: "back-to-back spacing")
                guard separation <= stock else {
                    throw FieldError.invalid("Second 90 spacing exceeds one 10-ft piece.")
                }
                extra += "\nSECOND 90 · STAR: measure " + separation.measurement(units) +
                    " from BACK of the first FINISHED 90, not the original tape zero. Hook faces remaining free end for matching shoes."
            }
            if mode == .kicked90 {
                let height = try positive(rise, label: "kick rise")
                let run = height / tan(angle * .pi / 180)
                guard run.isFinite && run <= stock else {
                    throw FieldError.invalid("Kick geometry cannot fit within the available 10-ft stock.")
                }
                extra += "\nKick projected travel " + run.measurement(units) +
                    " at " + String(format: "%.1f", angle) +
                    "°. This is NOT a physical kick mark. Locate it from a separate measured reference."
            }
            return BendPlan(marks: [PencilMark(id: "A", distance: mark, shoeReference: "ARROW")],
                            spacing: nil, shrink: nil, detail: extra,
                            warning: mode == .stub ? shoeWarning :
                                "Additional bends are NOT fully stock/clearance verified. Confirm developed length and shoe geometry.")
        }

        if mode == .saddle3 {
            let height = try positive(rise, label: "obstacle clearance rise")
            let obstacle = try positive(centre, label: "obstruction centre distance")
            let sideAngle = angle / 2
            let traditional = saddleMethod == "Traditional" && (angle == 30 || angle == 45)
            let spacing = height * (traditional ? (angle == 45 ? 2.5 : 3.8) : 1 / sin(sideAngle * .pi / 180))
            let shift = height * tan(sideAngle * .pi / 360)
            let b = obstacle + shift
            let points = [b-spacing, b, b+spacing]
            try validateMarks(points, stock: stock, applyShoeGap: traditional)
            return BendPlan(
                marks: zip(["A","B","C"],points).map {
                    PencilMark(id: $0.0, distance: $0.1,
                               shoeReference: traditional ? ($0.0 == "B" ? "CHECK NOTCH" : "CHECK ARROW") : "GEOMETRY ONLY")
                },
                spacing: spacing, shrink: 2 * shift,
                detail: "Centre bend " + angle.formatted() + "°, two outer bends " + sideAngle.formatted() +
                    "°. Traditional 2.5/3.8 multipliers assume manufacturer-compatible notch/arrow references.",
                warning: traditional
                    ? "Verify the exact bender supports the traditional notch/arrow method; minimum shoe clearance and physical arcs are not proven."
                    : "GEOMETRIC bend centres ONLY — not approved physical ARROW/NOTCH marks.")
        }

        if mode == .saddle4 {
            let height = try positive(rise, label: "obstacle rise")
            let near = try positive(distance, label: "near obstacle face")
            let wide = try positive(width, label: "obstruction width")
            let s = height / sin(angle * .pi / 180)
            let shrink = height * tan(angle * .pi / 360)
            let b = near + shrink
            let coords = [b-s,b,b+wide,b+wide+s]
            try validateMarks(coords, stock: stock, applyShoeGap: false)
            return BendPlan(
                marks: zip(["A","B","C","D"],coords).map { PencilMark(id: $0.0, distance: $0.1, shoeReference: "GEOMETRY ONLY") },
                spacing: s, shrink: 2 * shrink,
                detail: "Four bend-centre planning positions; the layout is two opposing offsets around the obstruction.",
                warning: "DO NOT use these geometric bend centres as physical bender marks; use verified shoe references and check the bend arcs.")
        }

        let up = try positive(rise, label: "centreline rise")
        let along = try positive(distance, label: "reference distance")
        let sideways = mode == .rolling ? try positive(roll, label: "sideways roll") : 0
        let trueOffset = hypot(up, sideways)
        let radians = angle * .pi / 180
        let spacing = trueOffset / sin(radians)
        let shrink = trueOffset * tan(radians / 2)
        let markA = farCritical ? along + shrink - spacing : along + shrink
        let markB = markA + spacing
        try validateMarks([markA, markB], stock: stock)
        let detail = farCritical
            ? "Far critical: B = reference distance + shrink; A = B − spacing."
            : "Near critical: A = reference distance + shrink; B = A + spacing."
        let clearance = minimumGap.trimmingCharacters(in: .whitespaces).isEmpty
            ? "Minimum shoe spacing is NOT VERIFIED. " + shoeWarning
            : "Minimum mark spacing checked against your input; actual shoe-head access is NOT VERIFIED. " + shoeWarning
        return BendPlan(marks: [PencilMark(id: "A", distance: markA, shoeReference: "CHECK ARROW"),
                                PencilMark(id: "B", distance: markB, shoeReference: "CHECK ARROW")],
                        spacing: spacing, shrink: shrink, detail: detail, warning: clearance)
    }

    private func validateMarks(_ marks: [Double], stock: Double, applyShoeGap: Bool = true) throws {
        guard marks.allSatisfy({ $0.isFinite && $0 >= 0 && $0 <= stock }) else {
            throw FieldError.invalid("CALCULATION NOT POSSIBLE: a plotted mark exceeds the 10-ft stock.")
        }
        let sorted = marks.sorted()
        for idx in 1..<sorted.count where sorted[idx] - sorted[idx-1] <= 0.000001 {
            throw FieldError.invalid("BENDS TOO CLOSE: physical bend marks overlap. Increase rise or separation.")
        }
        // Geometric saddle centres are not manufacturer-approved ARROW marks.
        // Validate stock for those plans, but never claim a shoe-gap check.
        guard applyShoeGap else { return }
        if !minimumGap.trimmingCharacters(in: .whitespaces).isEmpty {
            let gap = try positive(minimumGap, label: "minimum shoe spacing")
            guard gap <= stock else {
                throw FieldError.invalid("Verified shoe spacing cannot exceed the 10-ft stock.")
            }
            for idx in 1..<sorted.count where sorted[idx] - sorted[idx-1] + 0.000001 < gap {
                throw FieldError.invalid("BENDS TOO CLOSE: marks are closer than the verified shoe spacing.")
            }
        }
    }
}

private struct BendSketch: View {
    let mode: BendMode

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height
            Canvas { context, _ in
                var line = Path()
                if [.stub, .back, .kicked90].contains(mode) {
                    line.move(to: CGPoint(x: width * 0.27, y: 12))
                    line.addLine(to: CGPoint(x: width * 0.27, y: height * 0.74))
                    line.addLine(to: CGPoint(x: width - 14, y: height * 0.74))
                } else {
                    line.move(to: CGPoint(x: 14, y: height * 0.75))
                    line.addLine(to: CGPoint(x: width * 0.37, y: height * 0.75))
                    line.addLine(to: CGPoint(x: width * 0.64, y: height * 0.24))
                    line.addLine(to: CGPoint(x: width - 14, y: height * 0.24))
                }
                context.stroke(line, with: .color(WorkbenchStyle.cyan),
                               style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("TAPE 0")
                    Text([BendMode.stub, .back, .kicked90].contains(mode) ? "Short end" : "Reference end")
                }
                Spacer()
                Text([BendMode.stub, .back, .kicked90].contains(mode) ? "First 90°" : (mode == .rolling ? "Rise ↑ + roll ↔" : "Rise ↑"))
            }
            .font(.caption2.bold())
            .foregroundStyle(.white)
            .padding(10)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))
    }
}

private extension Double {
    func measurement(_ units: BendUnits) -> String {
        guard isFinite else { return "—" }
        if units == .mm {
            return "\(Int(rounded())) mm"
        }
        let sixteenths = Int((abs(self) * 16).rounded())
        let whole = sixteenths / 16
        let numerator = sixteenths % 16
        let sign = self < 0 ? "−" : ""
        if numerator == 0 { return "\(sign)\(whole) in" }
        func gcd(_ a: Int, _ b: Int) -> Int {
            var x = a, y = b
            while y != 0 { (x, y) = (y, x % y) }
            return x
        }
        let factor = gcd(numerator, 16)
        let fraction = "\(numerator / factor)/\(16 / factor)"
        return sign + (whole > 0 ? "\(whole) " : "") + fraction + " in"
    }
}
