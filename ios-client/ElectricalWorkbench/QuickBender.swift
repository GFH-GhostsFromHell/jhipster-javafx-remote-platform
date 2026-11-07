import SwiftUI
import Foundation

/// Fast field calculations. Dimensions are conduit-centreline geometry; shoe marks require verification.
private enum BendMode: String, CaseIterable, Identifiable {
    case offset = "Offset"
    case rolling = "Rolling"
    case stub = "90° stub"
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
                    Text("ENTER JOB DIMENSIONS")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(WorkbenchStyle.cyan)

                    if mode == .stub {
                        entry("Finished stub height (\(units.rawValue))", id: "stub", value: $finishedStub,
                              help: "From the short cut end; use the same reference convention as your shoe.")
                        entry("Actual shoe deduct (\(units.rawValue))", id: "deduct", value: $deduct,
                              help: "Read the value stamped on your exact bender or measure a test 90.")
                    } else {
                        entry("Centreline rise (\(units.rawValue))", id: "rise", value: $rise,
                              help: "Perpendicular change between the old and new conduit centreline.")
                        if mode == .rolling {
                            entry("Sideways roll (\(units.rawValue))", id: "roll", value: $roll,
                                  help: "Horizontal side shift, NOT the diagonal distance.")
                        }
                        entry("Reference distance (\(units.rawValue))", id: "distance", value: $distance,
                              help: "Along the run, from tape zero to your selected critical-bend line.")
                        Picker("Angle of each bend", selection: $angle) {
                            ForEach([10.0, 15.0, 22.5, 30.0, 45.0, 60.0], id: \.self) { a in
                                Text("\(a.formatted())°").tag(a)
                            }
                        }
                        .font(.subheadline)
                        .pickerStyle(.menu)

                        Picker("Critical bend", selection: $farCritical) {
                            Text("Near bend").tag(false)
                            Text("Far bend").tag(true)
                        }
                        .font(.subheadline)
                        .pickerStyle(.segmented)
                    }
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
                                Text("Measure from tape zero, then align the verified shoe reference.")
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
                        Label(plan.warning, systemImage: "checkmark.shield")
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
                    if showOptions && mode != .stub {
                        entry("Verified minimum mark spacing (\(units.rawValue))",
                              id: "gap", value: $minimumGap,
                              help: "Optional: enter the minimum spacing recommended for YOUR shoe. If unknown, clearance is not verified.")
                    }
                    Text("One straight 10-ft EMT length: \(units.stock.measurement(units)). A mark beyond the stock, or two marks closer than your entered minimum, is rejected. Bend arcs, hook access and springback still require a field check.")
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
            for converted in [$rise, $roll, $distance, $finishedStub, $deduct, $minimumGap] {
                if let value = Double(converted.wrappedValue), value.isFinite {
                    converted.wrappedValue = String(format: "%.3f", value * factor)
                }
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
        case .stub: "TAPE 0 = the short end of the finished 90°."
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
        }
    }

    private var sketchDescription: String {
        switch mode {
        case .stub: "An L-shaped conduit; tape zero is at the end of the short upright stub."
        case .offset: "Two equal bends lift the conduit from the starting line to a higher parallel line."
        case .rolling: "A two-bend offset with vertical rise and sideways horizontal roll."
        }
    }

    private var planState: PlanState {
        let needed = mode == .stub
            ? [finishedStub, deduct]
            : (mode == .rolling ? [rise, roll, distance] : [rise, distance])
        guard needed.allSatisfy({ !$0.trimmingCharacters(in: .whitespaces).isEmpty }) else {
            return .waiting(mode == .stub
                            ? "Enter finished stub height and your bender's verified deduct."
                            : "Enter rise and reference distance\(mode == .rolling ? " plus sideways roll" : "").")
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
        if mode == .stub {
            let required = try positive(finishedStub, label: "finished stub height")
            let takeup = try positive(deduct, label: "measured shoe deduct")
            guard required > takeup else {
                throw FieldError.invalid("Stub height must exceed your verified deduct.")
            }
            let mark = required - takeup
            guard mark <= stock && required <= stock else {
                throw FieldError.invalid("CALCULATION NOT POSSIBLE on one 10-ft EMT length.")
            }
            return BendPlan(marks: [PencilMark(id: "A", distance: mark, shoeReference: "ARROW")],
                            spacing: nil, shrink: nil,
                            detail: "Finished stub − verified deduct = ARROW distance. Check the finished dimension after bending.",
                            warning: shoeWarning)
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
        guard [markA, markB, spacing, shrink].allSatisfy({ $0.isFinite }),
              markA >= 0, markB <= stock else {
            throw FieldError.invalid("CALCULATION NOT POSSIBLE: marks fall outside one 10-ft EMT length. Change the layout, angle, or stock.")
        }
        if !minimumGap.trimmingCharacters(in: .whitespaces).isEmpty {
            let minGap = try positive(minimumGap, label: "minimum shoe spacing")
            guard spacing >= minGap else {
                throw FieldError.invalid("BENDS TOO CLOSE: this spacing is less than your bender's verified minimum.")
            }
        }
        let detail = farCritical
            ? "Far critical: B = reference distance + shrink; A = B − spacing."
            : "Near critical: A = reference distance + shrink; B = A + spacing."
        let clearance = minimumGap.isEmpty
            ? "Minimum shoe spacing is NOT VERIFIED. " + shoeWarning
            : shoeWarning
        return BendPlan(marks: [PencilMark(id: "A", distance: markA, shoeReference: "CHECK ARROW"),
                                PencilMark(id: "B", distance: markB, shoeReference: "CHECK ARROW")],
                        spacing: spacing, shrink: shrink, detail: detail, warning: clearance)
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
                if mode == .stub {
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
                    Text(mode == .stub ? "Short end" : "Reference end")
                }
                Spacer()
                Text(mode == .stub ? "Finished 90°" : (mode == .rolling ? "Rise ↑ + roll ↔" : "Rise ↑"))
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
