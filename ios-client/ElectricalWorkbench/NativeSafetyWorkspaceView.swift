import SwiftUI
import Foundation

private struct SafetyEntry: Codable, Identifiable {
    let id: UUID
    let kind: String
    let asset: String
    let notes: String
    let date: Date
}

@MainActor
final class SafetyLog: ObservableObject {
    @Published private(set) var records: [SafetyEntry] = []
    private var key: String

    init(username: String) {
        key = "ewb.ios.safety.\(username)"
        if let data = UserDefaults.standard.data(forKey: key),
           let recovered = try? JSONDecoder().decode([SafetyEntry].self, from: data) {
            records = recovered
        }
    }

    func add(kind: String, asset: String, notes: String) {
        records.insert(SafetyEntry(id: UUID(), kind: kind, asset: asset, notes: notes,
                                   date: Date()), at: 0)
        if records.count > 200 { records = Array(records.prefix(200)) }
        persist()
    }

    func remove(_ record: SafetyEntry) {
        records.removeAll { $0.id == record.id }
        persist()
    }

    private func persist() {
        if let encoded = try? JSONEncoder().encode(records) {
            UserDefaults.standard.set(encoded, forKey: key)
        }
    }
}

private enum SafetyTab: String, CaseIterable, Identifiable {
    case dashboard = "Dashboard"
    case lightning = "Thunderstorm"
    case ppe = "Arc-flash PPE"
    case approach = "Approach"
    case unsafe = "Unsafe conditions"
    case gloves = "Rubber gloves"
    case maintenance = "Maintenance"
    var id: String { rawValue }
}

struct NativeSafetyWorkspaceView: View {
    @EnvironmentObject private var account: CloudAccountSession
    @State private var tab: SafetyTab = .dashboard
    @StateObject private var log = SafetyLog(username: "local")

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("ELECTRICAL SAFETY")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Native field safety planning and records. Site procedures and a competent work authority remain controlling.")
                        .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
                    Picker("Safety workspace", selection: $tab) {
                        ForEach(SafetyTab.allCases) { option in
                            Text(option.rawValue).tag(option)
                        }
                    }
                    .pickerStyle(.menu)
                }
                .safetyCard()

                switch tab {
                case .dashboard: safetyDashboard
                case .lightning: LightningStandDownView(log: log)
                case .ppe: PPEComparisonView(log: log)
                case .approach: ApproachComparisonView()
                case .unsafe: SafetyCaptureView(log: log, kind: "Unsafe condition")
                case .gloves: GloveInspectionView(log: log)
                case .maintenance: SafetyMaintenanceView(log: log)
                }

                Text("The app cannot authorize energized work or replace hazard assessments, CSA Z462/CEC/OESC requirements, equipment instructions, lockout/tagout or your employer's permit system.")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Safety")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            log.configure(account.username)
        }
    }

    private var safetyDashboard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SAVED SAFETY RECORDS").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            ForEach(SafetyTab.allCases.filter { [.lightning,.ppe,.unsafe,.gloves,.maintenance].contains($0) }) { item in
                HStack {
                    Text(item.rawValue)
                    Spacer()
                    Text("\(log.records.filter { $0.kind.localizedCaseInsensitiveContains(item == .lightning ? "Thunder" : (item == .ppe ? "PPE" : item == .unsafe ? "Unsafe" : item == .gloves ? "Glove" : "Maintenance")) }.count)")
                        .monospacedDigit().foregroundStyle(WorkbenchStyle.cyan)
                }
                .font(.subheadline)
            }
            Divider()
            Text("Recent records").font(.headline)
            if log.records.isEmpty {
                Text("No safety records saved on this device yet.")
                    .foregroundStyle(WorkbenchStyle.muted)
            }
            ForEach(log.records.prefix(20)) { record in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(record.kind).font(.subheadline.bold())
                        Spacer()
                        Button(role: .destructive) { log.remove(record) } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                    Text(record.asset).font(.caption)
                    Text(record.notes).font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    Text(record.date, style: .date).font(.caption2)
                }
                Divider()
            }
        }
        .safetyCard()
    }
}

private extension SafetyLog {
    func configure(_ username: String) {
        let newKey = "ewb.ios.safety.\(username)"
        if key != newKey {
            key = newKey
            records = []
            if let data = UserDefaults.standard.data(forKey: key),
               let restored = try? JSONDecoder().decode([SafetyEntry].self, from: data) {
                records = restored
            }
        }
    }
}

private struct LightningStandDownView: View {
    @ObservedObject var log: SafetyLog
    @AppStorage("ewb.ios.storm.lastTime") private var lastStrike = 0.0
    @State private var site = ""
    @State private var notes = ""
    @State private var timeSeconds = "30"

    private func standDown(kind: String) {
        lastStrike = Date().timeIntervalSince1970
        log.add(kind: "Thunderstorm – \(kind)", asset: site, notes: notes)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("THUNDERSTORM STAND-DOWN").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            Text("Record observed lightning/thunder and apply site-directed stand-down. The public 30-minute timer never grants permission to resume.")
                .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
            safetyField("Site / substation", $site)
            safetyField("Observation notes", $notes)

            TimelineView(.periodic(from: .now, by: 1)) { timeline in
                let remaining = max(0, 30 * 60 - Int(timeline.date.timeIntervalSince1970 - lastStrike))
                if lastStrike == 0 {
                    Text("No active stand-down event recorded.").foregroundStyle(WorkbenchStyle.muted)
                } else if remaining > 0 {
                    Label("STAND DOWN · \(remaining / 60)m \(remaining % 60)s remaining",
                          systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.orange).font(.headline.monospacedDigit())
                } else {
                    Text("30 minutes elapsed. Work is NOT automatically cleared. Obtain site authorization.")
                        .foregroundStyle(.orange)
                }
            }
            Button("Thunder heard · Start/reset") { standDown(kind: "thunder heard") }
                .buttonStyle(.borderedProminent)
            Button("Lightning observed · Start/reset") { standDown(kind: "lightning observed") }
                .buttonStyle(.bordered)
            Button("Record supervisor stand-down") { standDown(kind: "supervisor stand-down") }
                .buttonStyle(.bordered)
            Button("Record restart authorization (supervisor/site only)") {
                log.add(kind: "Thunderstorm – restart authorized", asset: site, notes: notes)
                lastStrike = 0
            }
            .buttonStyle(.bordered)

            safetyField("Flash-to-thunder delay (seconds)", $timeSeconds, numeric: true)
            if let seconds = Double(timeSeconds), seconds >= 0 {
                Text(String(format: "Approximate lightning distance %.1f km (seconds ÷ 3). This estimate is NOT work clearance.", seconds / 3))
                    .font(.caption).foregroundStyle(WorkbenchStyle.muted)
            }
            Link("Official ECCC lightning map", destination: URL(string: "https://weather.gc.ca/?layers=lightning")!)
                .font(.subheadline)
        }
        .safetyCard()
    }
}

private struct PPEComparisonView: View {
    @ObservedObject var log: SafetyLog
    @State private var task = ""
    @State private var voltage = ""
    @State private var incident = ""
    @State private var garment = ""
    @State private var face = ""
    @State private var gloves = ""

    private var evaluated: String? {
        guard let v = Double(voltage), v > 0,
              let e = Double(incident), e > 0,
              let g = Double(garment), g > 0,
              let f = Double(face), f > 0,
              let rating = Double(gloves), rating > 0 else { return nil }
        return "Incident energy \(e) cal/cm². Garment \(g >= e ? "meets entered energy" : "INSUFFICIENT"), face \(f >= e ? "meets entered energy" : "INSUFFICIENT"), glove maximum-use voltage \(rating >= v ? "not below system voltage" : "INSUFFICIENT"). This is NOT an assembled PPE selection or work authorization."
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ARC-FLASH LABEL RATING COMPARISON").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            Text("Enter incident energy from an authorized arc-flash study/label. Compare ratings only; required PPE includes much more than these three numbers.")
                .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
            safetyField("Equipment / task", $task)
            safetyField("System voltage (V)", $voltage, numeric: true)
            safetyField("Authorized incident energy (cal/cm²)", $incident, numeric: true)
            safetyField("Garment arc rating (cal/cm²)", $garment, numeric: true)
            safetyField("Face/head protection rating (cal/cm²)", $face, numeric: true)
            safetyField("Glove maximum-use voltage (V)", $gloves, numeric: true)
            Text(evaluated ?? "Enter all positive ratings to compare.")
                .font(.subheadline).foregroundStyle(.orange)
                .fixedSize(horizontal: false, vertical: true)
            Button("Save assessment record") {
                if let evaluated {
                    log.add(kind: "PPE comparison", asset: task, notes: evaluated)
                }
            }
            .disabled(evaluated == nil)
            .buttonStyle(.borderedProminent)
        }
        .safetyCard()
    }
}

private struct ApproachComparisonView: View {
    @State private var equipment = ""
    @State private var voltage = ""
    @State private var required = ""
    @State private var actual = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("APPROACH-BOUNDARY PLANNER").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            Text("Obtain the limited/restricted approach boundary from the applicable qualified study and Code/table. This tool does NOT invent boundary distances.")
                .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
            safetyField("Equipment / electrical work", $equipment)
            safetyField("Nominal voltage (V)", $voltage, numeric: true)
            safetyField("Applicable required boundary (mm)", $required, numeric: true)
            safetyField("Planned worker/equipment clearance (mm)", $actual, numeric: true)
            if let boundary = Double(required), let proposed = Double(actual),
               boundary > 0, proposed > 0 {
                Label(proposed >= boundary
                      ? "Entered clearance exceeds entered boundary; authorization and all other controls still required."
                      : "INSUFFICIENT clearance for the boundary you entered.",
                      systemImage: "exclamationmark.shield")
                    .font(.subheadline)
                    .foregroundStyle(proposed >= boundary ? WorkbenchStyle.cyan : .orange)
            }
        }
        .safetyCard()
    }
}

private struct SafetyCaptureView: View {
    @ObservedObject var log: SafetyLog
    let kind: String
    @State private var asset = ""
    @State private var notes = ""
    @State private var mitigation = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("UNSAFE CONDITION / NEAR MISS").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            safetyField("Site, asset or work order", $asset)
            safetyField("Condition / severity / potential consequence", $notes)
            safetyField("Immediate isolation, correction and notification", $mitigation)
            Text("For urgent hazards, stop work and use employer reporting channels immediately. Saving this record does not send a notification.")
                .font(.caption).foregroundStyle(.orange)
            Button("Save local unsafe-condition record") {
                log.add(kind: kind, asset: asset, notes: notes + "\nActions: " + mitigation)
                asset = ""; notes = ""; mitigation = ""
            }
            .buttonStyle(.borderedProminent)
            .disabled(asset.isEmpty || notes.isEmpty)
        }
        .safetyCard()
    }
}

private struct GloveInspectionView: View {
    @ObservedObject var log: SafetyLog
    @State private var classLabel = ""
    @State private var testDate = ""
    @State private var identifier = ""
    @State private var findings = ""
    @State private var inspected = false
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("RUBBER INSULATING GLOVE RECORD").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            Text("Record visual/air inspections, glove class and dielectric-test documentation. Service intervals, voltage limits and acceptance are determined by applicable standards and employer procedures.")
                .font(.caption).foregroundStyle(WorkbenchStyle.muted)
            safetyField("Glove set ID", $identifier)
            safetyField("Class from manufacturer label", $classLabel)
            safetyField("Last dielectric test date (YYYY-MM-DD)", $testDate)
            Toggle("Authorized physical inspection completed", isOn: $inspected)
                .tint(WorkbenchStyle.cyan)
            safetyField("Condition, defects or restrictions", $findings)
            Button("Save glove record") {
                log.add(kind: "Glove inspection", asset: identifier,
                        notes: "Class \(classLabel); test \(testDate); inspected \(inspected); \(findings)")
                identifier = ""; findings = ""; inspected = false
            }
            .buttonStyle(.borderedProminent)
            .disabled(identifier.isEmpty || classLabel.isEmpty || findings.isEmpty)
        }
        .safetyCard()
    }
}

private struct SafetyMaintenanceView: View {
    @ObservedObject var log: SafetyLog
    @State private var equipment = ""
    @State private var lastService = ""
    @State private var nextService = ""
    @State private var condition = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ELECTRICAL SAFETY MAINTENANCE").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
            safetyField("Equipment ID", $equipment)
            safetyField("Last inspection (YYYY-MM-DD)", $lastService)
            safetyField("Scheduled inspection (YYYY-MM-DD)", $nextService)
            safetyField("Inspection result / deficiencies", $condition)
            Text("An entered due date is not a manufacturer- or standard-approved maintenance interval.")
                .font(.caption).foregroundStyle(WorkbenchStyle.muted)
            Button("Save maintenance record") {
                log.add(kind: "Maintenance", asset: equipment,
                        notes: "Last: \(lastService); due: \(nextService). \(condition)")
                equipment = ""; condition = ""
            }
            .buttonStyle(.borderedProminent)
            .disabled(equipment.isEmpty || condition.isEmpty)
        }
        .safetyCard()
    }
}

private func safetyField(_ label: String, _ value: Binding<String>, numeric: Bool = false) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(label).font(.subheadline.weight(.medium))
        TextField("Enter \(label.lowercased())", text: value)
            .keyboardType(numeric ? .decimalPad : .default)
            .padding(10)
            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
    }
}

private struct SafetyCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func safetyCard() -> some View { modifier(SafetyCard()) }
}
