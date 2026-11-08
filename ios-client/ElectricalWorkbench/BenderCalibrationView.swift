import SwiftUI

/// Native tool-specific measurements are kept on this device, not shared with Android.
struct BenderCalibrationView: View {
    @AppStorage("ewb.ios.calibration.name") private var savedName = ""
    @AppStorage("ewb.ios.calibration.model") private var savedModel = ""
    @AppStorage("ewb.ios.calibration.size") private var savedSize = ""
    @AppStorage("ewb.ios.calibration.material") private var savedMaterial = "EMT"
    @AppStorage("ewb.ios.calibration.deduct.mm") private var savedDeductMm = 0.0
    @AppStorage("ewb.ios.calibration.gain.mm") private var savedGainMm = 0.0
    @AppStorage("ewb.ios.calibration.minMarkGap.mm") private var savedMinMarkGapMm = 0.0
    @AppStorage("ewb.ios.calibration.minStraight.mm") private var savedMinStraightMm = 0.0

    @State private var name = ""
    @State private var model = ""
    @State private var tradeSize = "1/2"
    @State private var material = "EMT"
    @State private var useInches = false

    @State private var arrowMark = ""
    @State private var finishedStub = ""
    @State private var originalStock = ""
    @State private var firstLeg = ""
    @State private var secondLeg = ""
    @State private var minimumMarkGap = ""
    @State private var minimumArcStraight = ""
    @State private var notice = ""

    private var unit: String { useInches ? "in" : "mm" }
    private var factor: Double { useInches ? 25.4 : 1 }

    private var deduct: Double? {
        guard let arrow = Double(arrowMark), arrow >= 0,
              let height = Double(finishedStub), height > arrow else { return nil }
        return height - arrow
    }

    private var gain: Double? {
        guard let stock = Double(originalStock), stock > 0,
              let a = Double(firstLeg), a > 0,
              let b = Double(secondLeg), b > 0,
              a + b >= stock else { return nil }
        return a + b - stock
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("YOUR BENDER · NOT A GENERIC TABLE")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Measure the exact shoe and conduit trade size using scrap. Save the measured deduct or gain locally.")
                        .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
                }
                .calibrationCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("Bender identification").font(.headline)
                    field("Profile name", value: $name)
                    field("Make / model", value: $model)
                    field("Conduit trade size (e.g., 3/4)", value: $tradeSize)
                    field("Material (EMT, IMC, RMC)", value: $material)
                    Toggle("Use inches instead of mm", isOn: $useInches)
                        .tint(WorkbenchStyle.cyan)
                }
                .calibrationCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("1 · Measure deduct with a scrap 90").font(.headline)
                    Text("Tape zero = square short end. Mark at the arrow, bend 90°, then measure the finished stub using the shoe maker's reference.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    numeric("Arrow mark from short end (\(unit))", value: $arrowMark)
                    numeric("Measured finished stub height (\(unit))", value: $finishedStub)
                    Text(deduct.map { "MEASURED DEDUCT: \($0.formatted(.number.precision(.fractionLength(2)))) \(unit)" } ?? "Enter both measurements to find deduct.")
                        .font(.subheadline.bold())
                        .foregroundStyle(WorkbenchStyle.cyan)
                }
                .calibrationCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("2 · Measure gain with a scrap 90").font(.headline)
                    Text("Record straight stock before bending. After bending, measure both finished legs using consistent back-of-bend references.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    numeric("Original straight stock (\(unit))", value: $originalStock)
                    numeric("First finished leg (\(unit))", value: $firstLeg)
                    numeric("Second finished leg (\(unit))", value: $secondLeg)
                    Text(gain.map { "MEASURED GAIN PER 90: \($0.formatted(.number.precision(.fractionLength(2)))) \(unit)" } ?? "Enter stock and finished legs to find gain.")
                        .font(.subheadline.bold())
                        .foregroundStyle(WorkbenchStyle.cyan)
                }
                .calibrationCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("3 · Verified minimum shoe mark spacing").font(.headline)
                    Text("Optional: record the minimum mark spacing for this exact shoe, conduit size and material only after checking a manufacturer instruction or measured test. This is NOT the same as the minimum straight length between bend arcs.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    numeric("Verified minimum spacing (\(unit))", value: $minimumMarkGap)
                    numeric("Verified straight between bend arcs (\(unit))", value: $minimumArcStraight)
                }
                .calibrationCard()

                VStack(alignment: .leading, spacing: 12) {
                    Button {
                        save()
                    } label: {
                        Label("Save measured bender profile", systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity, minHeight: 45)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(WorkbenchStyle.cyan)
                    .foregroundStyle(WorkbenchStyle.background)

                    if !notice.isEmpty {
                        Text(notice)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                    if savedDeductMm > 0 || savedGainMm > 0 || savedMinMarkGapMm > 0 || savedMinStraightMm > 0 {
                        Divider()
                        Text("SAVED ON THIS DEVICE").font(.caption.bold())
                        Text(savedName + " • " + savedSize + " " + savedMaterial)
                        Text("Deduct: " + (savedDeductMm > 0 ? format(savedDeductMm / factor) : "Not measured") +
                             " \(unit)  •  Gain: " +
                             (savedGainMm > 0 ? format(savedGainMm / factor) : "Not measured") +
                             " \(unit)")
                            .font(.subheadline)
                        Text("Verified mark gap: " + (savedMinMarkGapMm > 0 ? format(savedMinMarkGapMm / factor) + " \(unit)" : "Not measured"))
                            .font(.subheadline)
                        Text("Verified straight between arcs: " + (savedMinStraightMm > 0 ? format(savedMinStraightMm / factor) + " \(unit)" : "Not measured"))
                            .font(.subheadline)
                    }
                }
                .calibrationCard()

                Text("Only reuse this profile for the same verified physical shoe and conduit size. This offline profile does not automatically sync to web, Android, or another iPhone.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Bender calibration")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if name.isEmpty { name = savedName }
            if model.isEmpty { model = savedModel }
            if !savedSize.isEmpty { tradeSize = savedSize }
            material = savedMaterial
            if minimumMarkGap.isEmpty && savedMinMarkGapMm > 0 {
                minimumMarkGap = format(savedMinMarkGapMm / factor)
            }
            if minimumArcStraight.isEmpty && savedMinStraightMm > 0 {
                minimumArcStraight = format(savedMinStraightMm / factor)
            }
        }
        .onChange(of: useInches) { previous, current in
            guard previous != current else { return }
            let multiplier = current ? (1 / 25.4) : 25.4
            for value in [$arrowMark, $finishedStub, $originalStock, $firstLeg, $secondLeg, $minimumMarkGap, $minimumArcStraight] {
                if let original = Double(value.wrappedValue), original.isFinite {
                    value.wrappedValue = format(original * multiplier)
                }
            }
        }
    }

    private func field(_ title: String, value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption.weight(.semibold))
            TextField(title, text: value)
                .textInputAutocapitalization(.words)
                .padding(10)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func numeric(_ title: String, value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption.weight(.semibold))
            TextField("Measured value", text: value)
                .keyboardType(.decimalPad)
                .padding(10)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func save() {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty,
              !model.trimmingCharacters(in: .whitespaces).isEmpty,
              !material.trimmingCharacters(in: .whitespaces).isEmpty,
              !tradeSize.trimmingCharacters(in: .whitespaces).isEmpty else {
            notice = "Enter profile name, shoe make/model, material, and trade size."
            return
        }
        let gapInput = minimumMarkGap.trimmingCharacters(in: .whitespaces)
        let measuredGap = gapInput.isEmpty ? 0 : (Double(gapInput) ?? -1)
        guard measuredGap.isFinite, measuredGap >= 0, measuredGap * factor <= 3048,
              (gapInput.isEmpty || measuredGap > 0) else {
            notice = "Enter a positive, verified spacing no greater than 10 ft, or leave the shoe gap blank."
            return
        }
        let straightInput = minimumArcStraight.trimmingCharacters(in: .whitespaces)
        let measuredStraight = straightInput.isEmpty ? 0 : (Double(straightInput) ?? -1)
        guard measuredStraight.isFinite, measuredStraight >= 0, measuredStraight * factor <= 3048,
              (straightInput.isEmpty || measuredStraight > 0) else {
            notice = "Enter a positive verified straight between bend arcs within 10 ft, or leave it blank."
            return
        }
        let deductStarted = !arrowMark.isEmpty || !finishedStub.isEmpty
        let gainStarted = !originalStock.isEmpty || !firstLeg.isEmpty || !secondLeg.isEmpty
        guard (deductStarted || gainStarted || measuredGap > 0 || measuredStraight > 0),
              (!deductStarted || deduct != nil),
              (!gainStarted || gain != nil) else {
            notice = "Complete at least one valid measurement set. Correct partial or impossible values."
            return
        }
        savedName = name
        savedModel = model
        savedMaterial = material
        savedSize = tradeSize
        savedDeductMm = (deduct ?? 0) * factor
        savedGainMm = (gain ?? 0) * factor
        savedMinMarkGapMm = measuredGap * factor
        savedMinStraightMm = measuredStraight * factor
        NotificationCenter.default.post(name: .ewbLocalRecordChanged, object: "benderProfiles")
        notice = "Saved locally. Account sync updates matching bender profiles when online."
    }

    private func format(_ value: Double) -> String {
        String(format: "%.3f", value)
    }
}

private struct CalibrationCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}

private extension View {
    func calibrationCard() -> some View {
        modifier(CalibrationCard())
    }
}
