import SwiftUI
import Foundation

private enum FabricationMode: String, CaseIterable, Identifiable {
    case gain = "Gain & cut"
    case rack = "Parallel rack"
    var id: String { rawValue }
}

struct FabricationView: View {
    @State private var mode: FabricationMode = .gain
    @State private var inches = false
    @State private var twoBends = false
    @State private var first = ""
    @State private var second = ""
    @State private var between = ""
    @State private var measuredGain = ""
    @State private var scrapStock = ""
    @State private var scrapA = ""
    @State private var scrapB = ""

    @State private var angle: Double = 30
    @State private var pipeSpacing = ""
    @State private var initialMark = ""
    @State private var count = 4
    @State private var increase = true

    @AppStorage("ewb.ios.calibration.gain.mm") private var savedGainMm = 0.0
    @AppStorage("ewb.ios.calibration.name") private var savedName = ""

    private var unit: String { inches ? "in" : "mm" }
    private var factor: Double { inches ? 25.4 : 1 }
    private var stockLimit: Double { inches ? 120 : 3048 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("FABRICATION · OFFLINE").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Tool-specific gain or parallel offsets. Do not substitute geometry for physical ARROW/STAR references.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
                .fabricationCard()

                VStack(alignment: .leading, spacing: 10) {
                    Picker("Fabrication tool", selection: $mode) {
                        ForEach(FabricationMode.allCases) { item in Text(item.rawValue).tag(item) }
                    }
                    .pickerStyle(.segmented)
                    Toggle("Measure in inches", isOn: $inches)
                        .tint(WorkbenchStyle.cyan)
                }
                .fabricationCard()

                if mode == .gain {
                    VStack(alignment: .leading, spacing: 9) {
                        Text("FINISHED JOB DIMENSIONS").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        Toggle("Back-to-back · two 90s", isOn: $twoBends)
                            .tint(WorkbenchStyle.cyan)
                        numeric("First finished leg (\(unit))", $first)
                        numeric("Second finished leg (\(unit))", $second)
                        if twoBends {
                            numeric("Distance between backs of 90s (\(unit))", $between)
                        }
                        numeric("Measured gain PER 90 (\(unit))", $measuredGain)
                        if savedGainMm > 0 {
                            Button("Use saved gain · " + savedName) {
                                measuredGain = String(format: "%.3f", savedGainMm / factor)
                            }
                            .font(.caption.weight(.semibold))
                        }
                        NavigationLink("Measure / edit shoe calibration") {
                            BenderCalibrationView()
                        }
                        .font(.caption)
                    }
                    .fabricationCard()

                    VStack(alignment: .leading, spacing: 9) {
                        Text("SCRAP TEST TO FIND GAIN").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        Text("Measure straight stock before bending, then both finished legs from consistent back-of-bend references.")
                            .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                        numeric("Original straight scrap length (\(unit))", $scrapStock)
                        numeric("First finished test leg (\(unit))", $scrapA)
                        numeric("Second finished test leg (\(unit))", $scrapB)
                        Button("Calculate and use scrap gain") {
                            guard let stock = positive(scrapStock),
                                  let a = positive(scrapA), let b = positive(scrapB),
                                  a + b >= stock else { return }
                            measuredGain = String(format: "%.3f", a + b - stock)
                        }
                        .disabled(!scrapValid)
                        .buttonStyle(.bordered)
                    }
                    .fabricationCard()
                } else {
                    VStack(alignment: .leading, spacing: 9) {
                        Text("PARALLEL RACK MARKS").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        Text("Measure spacing between adjacent conduits centre to centre; enter the first mark from the SAME reference on the first pipe.")
                            .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                        numeric("Adjacent conduit spacing (\(unit))", $pipeSpacing)
                        numeric("First conduit mark from tape 0 (\(unit))", $initialMark)
                        Picker("Bend angle", selection: $angle) {
                            ForEach([10.0, 15.0, 22.5, 30.0, 45.0, 60.0], id: \.self) { a in
                                Text("\(a.formatted())°").tag(a)
                            }
                        }
                        Stepper("Number of conduits: \(count)", value: $count, in: 2...20)
                        Toggle("Increase adjacent marks", isOn: $increase)
                            .tint(WorkbenchStyle.cyan)
                    }
                    .fabricationCard()
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text(mode == .gain ? "STOCK TO CUT" : "PARALLEL RACK RESULTS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    switch outcome {
                    case .waiting(let message):
                        Text(message).foregroundStyle(WorkbenchStyle.muted)
                    case .invalid(let message):
                        Label(message, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.orange)
                    case .ready(let lines):
                        ForEach(lines, id: \.self) { line in
                            Text(line)
                                .font(.subheadline.monospacedDigit())
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .fabricationCard()
                Text("Stock limit: 120 in / 3,048 mm. Check springback, shoe gain and radius. Moving a rack mark does NOT eliminate the need to verify both offset marks and installation clearances.")
                    .font(.footnote).foregroundStyle(WorkbenchStyle.muted)
                NavigationLink {
                    PortalScreen(title: "Advanced bending layouts", path: "/bend-calcs.html")
                } label: {
                    Label("Open advanced visual bending worksheets", systemImage: "arrow.up.right.square")
                        .font(.subheadline)
                }
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Conduit fabrication")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: inches) { before, after in
            guard before != after else { return }
            let multiplier = after ? (1 / 25.4) : 25.4
            for value in [$first,$second,$between,$measuredGain,$scrapStock,$scrapA,$scrapB,$pipeSpacing,$initialMark] {
                if let original = Double(value.wrappedValue), original.isFinite {
                    value.wrappedValue = String(format: "%.3f", original * multiplier)
                }
            }
        }
    }

    private func numeric(_ label: String, _ value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption.weight(.semibold))
            TextField("Measured value", text: value)
                .keyboardType(.decimalPad)
                .padding(11)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func positive(_ input: String) -> Double? {
        guard let amount = Double(input), amount.isFinite, amount > 0 else { return nil }
        return amount
    }

    private var scrapValid: Bool {
        guard let stock = positive(scrapStock), let a = positive(scrapA), let b = positive(scrapB) else { return false }
        return a + b >= stock
    }

    private enum Outcome {
        case waiting(String)
        case invalid(String)
        case ready([String])
    }

    private var outcome: Outcome {
        if mode == .gain {
            guard !first.isEmpty && !second.isEmpty && !measuredGain.isEmpty &&
                  (!twoBends || !between.isEmpty) else {
                return .waiting("Enter finished legs and a MEASURED gain for your exact bender.")
            }
            guard let a = positive(first), let b = positive(second),
                  let gain = Double(measuredGain), gain.isFinite, gain >= 0 else {
                return .invalid("Enter valid finished legs and nonnegative measured gain.")
            }
            let betweenBends: Double
            if twoBends {
                guard let middle = positive(between) else { return .invalid("Measure positive back-to-back spacing.") }
                betweenBends = middle
            } else {
                betweenBends = 0
            }
            let n = twoBends ? 2.0 : 1.0
            let length = a + b + betweenBends - n * gain
            guard length.isFinite && length > 0 && length <= stockLimit else {
                return .invalid("CALCULATION NOT POSSIBLE: precut length must fit one 10-ft EMT stock.")
            }
            return .ready([
                "Straight stock: " + display(length),
                "Shoe gain allowance: " + display(gain * n),
                "Formula: finished legs + middle − (number of 90s × measured gain)",
                "Physical ARROW/STAR marks are laid out separately using your verified shoe."
            ])
        }
        guard !pipeSpacing.isEmpty && !initialMark.isEmpty else {
            return .waiting("Enter adjacent conduit centre spacing and the first tape mark.")
        }
        guard let spacing = positive(pipeSpacing),
              let start = positive(initialMark) else {
            return .invalid("Use a positive pipe spacing and first mark.")
        }
        let stagger = spacing * tan(angle * .pi / 360)
        let marks = (0..<count).map { start + (increase ? 1.0 : -1.0) * Double($0) * stagger }
        guard marks.allSatisfy({ $0.isFinite && $0 >= 0 && $0 <= stockLimit }) else {
            return .invalid("CALCULATION NOT POSSIBLE: at least one pipe mark lies outside 10-ft stock.")
        }
        return .ready(["Adjacent stagger: " + display(stagger)] +
                      marks.enumerated().map { (i, mark) in
                          "Pipe \(i + 1) reference mark: " + display(mark)
                      } +
                      ["Check BOTH marks on every conduit; the reference move applies to each bend."])
    }

    private func display(_ value: Double) -> String {
        inches
            ? String(format: "%.3f in", value)
            : String(format: "%.1f mm", value)
    }
}

private struct FabricationCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func fabricationCard() -> some View { modifier(FabricationCard()) }
}
