import SwiftUI
import Foundation

struct FieldRecord: Codable, Identifiable {
    let id: UUID
    let category: String
    let title: String
    let notes: String
    let amount: Double
    let created: Date
    var syncId: String? = nil
}

@MainActor
final class NativeFieldRecordStore: ObservableObject {
    @Published private(set) var records: [FieldRecord] = []
    private var storageKey = ""
    func configure(_ user: String, force: Bool = false) {
        let key = "ewb.ios.workrecords.\(user)"
        guard force || storageKey != key else { return }
        storageKey = key
        if let data = UserDefaults.standard.data(forKey: key),
           let loaded = try? JSONDecoder().decode([FieldRecord].self, from: data) {
            records = loaded
        } else {
            records = []
        }
    }
    func add(category: String, title: String, notes: String, amount: Double = 0) {
        records.insert(FieldRecord(id: UUID(), category: category, title: title, notes: notes,
                                   amount: amount, created: Date()), at: 0)
        if records.count > 500 { records = Array(records.prefix(500)) }
        persist()
    }
    func remove(_ item: FieldRecord) {
        records.removeAll { $0.id == item.id }
        persist()
    }
    private func persist() {
        guard !storageKey.isEmpty, let data = try? JSONEncoder().encode(records) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
        NotificationCenter.default.post(name: .ewbLocalRecordChanged, object: "workrecords")
    }
    var exported: String {
        var report = "Electrical Workbench · native iOS field records\n"
        for item in records {
            report += "\(item.created.formatted(date: .abbreviated, time: .shortened)) | \(item.category) | \(item.title) | \(item.amount)\n\(item.notes)\n\n"
        }
        return report
    }
}

struct NativeWorkspacesView: View {
    @EnvironmentObject private var account: CloudAccountSession
    @StateObject private var store = NativeFieldRecordStore()

    var body: some View {
        List {
            Section {
                NavigationLink {
                    NativeLearnView()
                } label: {
                    Label("Learn · CEC navigator, formulas, controls", systemImage: "book")
                }
                NavigationLink {
                    NativeFieldToolsView(store: store)
                } label: {
                    Label("Field · nameplates, commissioning, inspection", systemImage: "wrench.and.screwdriver")
                }
                NavigationLink {
                    NativeProjectView(store: store)
                } label: {
                    Label("Projects · material takeoffs and cable planning", systemImage: "folder")
                }
                NavigationLink {
                    NativeCareerView(store: store)
                } label: {
                    Label("Career · trade hours, credentials and wages", systemImage: "briefcase")
                }
            } header: {
                Text("Native offline workspaces")
            } footer: {
                Text("Records in these screens remain on this device and are grouped by the last verified portal username. Cloud projects use different storage and currently do not synchronize with these native logs.")
            }

            Section("Saved field notes and data") {
                Text("\(store.records.count) local records")
                    .font(.subheadline)
                    .foregroundStyle(WorkbenchStyle.cyan)
                if !store.records.isEmpty {
                    ShareLink(item: store.exported) {
                        Label("Export / share readable field records", systemImage: "square.and.arrow.up")
                    }
                }
                ForEach(store.records.prefix(35)) { note in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(note.title).font(.subheadline.bold())
                            Spacer()
                            Button(role: .destructive) {
                                store.remove(note)
                            } label: { Image(systemName: "trash") }
                            .buttonStyle(.borderless)
                        }
                        Text(note.category + " · " +
                             note.created.formatted(date: .abbreviated, time: .omitted))
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                        Text(note.notes)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                            .lineLimit(4)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(WorkbenchStyle.background)
        .navigationTitle("Workspaces")
        .onAppear { store.configure(account.username) }
        .onReceive(NotificationCenter.default.publisher(for: .ewbAccountSyncUpdated)) { _ in
            store.configure(account.username, force: true)
        }
    }
}

private struct TopicSummary: Identifiable {
    let title: String
    let reference: String
    let summary: String
    var id: String { title }
}

struct NativeLearnView: View {
    @State private var search = ""
    @State private var controlStart = false
    @State private var controlStop = true
    @State private var overloadHealthy = true
    @State private var held = false

    private let topics: [TopicSummary] = [
        .init(title: "General rules", reference: "CEC Section 2", summary: "Approved equipment, manufacturer's markings and installation conditions must be verified."),
        .init(title: "Conductor sizing and ampacity", reference: "CEC Section 4", summary: "Check material, terminal temperature, correction factors and installation method."),
        .init(title: "Service demand", reference: "CEC Section 8", summary: "Choose the occupancy-specific load and demand worksheet, not a generic formula."),
        .init(title: "Grounding and bonding", reference: "CEC Section 10", summary: "Distinguish grounding electrodes, bonding conductor and derived system connections."),
        .init(title: "Raceways and boxes", reference: "CEC Section 12", summary: "Review raceway fill, support, physical protection and pull/box dimensions."),
        .init(title: "Overcurrent protection", reference: "CEC Section 14", summary: "Review device selection, 80/100% continuous use and equipment markings."),
        .init(title: "Class 1 / Class 2", reference: "CEC Section 16", summary: "Check circuit source limits, cable rating and separation requirements."),
        .init(title: "Transformers and equipment", reference: "CEC Section 26", summary: "Calculate transformer FLA and apply the correct primary/secondary OCPD conditions."),
        .init(title: "Motors and controls", reference: "CEC Section 28", summary: "Motor conductor, branch OCPD, overload and feeder requirements are different calculations."),
        .init(title: "Fire alarm", reference: "CEC Section 32", summary: "Follow listed panel instructions, circuit supervision and alarm system requirements.")
    ]
    private var filtered: [TopicSummary] {
        topics.filter { search.isEmpty ||
            $0.title.localizedCaseInsensitiveContains(search) ||
            $0.reference.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("CEC 2024 · ORIGINAL STUDY NAVIGATOR")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    TextField("Search section or topic", text: $search)
                        .padding(10)
                        .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                    ForEach(filtered) { item in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title).font(.subheadline.bold())
                            Text(item.reference).font(.caption).foregroundStyle(WorkbenchStyle.cyan)
                            Text(item.summary).font(.caption).foregroundStyle(WorkbenchStyle.muted)
                        }
                        Divider()
                    }
                    Text("These are study prompts, not reproduced CSA Code tables or authoritative rule text.")
                        .font(.caption).foregroundStyle(.orange)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 8) {
                    Text("FORMULA LIBRARY").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    ForEach([
                        "Three-phase FLA: I = VA ÷ (√3 × line voltage)",
                        "Single-phase FLA: I = VA ÷ voltage",
                        "Offset spacing: true rise ÷ sin(bend angle)",
                        "Offset shrink (geometric): rise × tan(angle ÷ 2)",
                        "Voltage drop: phase factor × K × I × one-way ft ÷ circular mils",
                        "CT ratio: primary A = secondary A × CT primary rating ÷ CT secondary rating",
                        "Hydraulic force: pressure (psi) × piston area (in²)",
                        "4–20 mA: 4 + 16 × (setpoint − min) ÷ (max − min)"
                    ], id: \.self) { formula in
                        Text(formula).font(.subheadline)
                        Divider().opacity(0.2)
                    }
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("THREE-WIRE START / STOP TRAINER").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Toggle("STOP contact healthy (NC closed)", isOn: $controlStop)
                    Toggle("Overload NC contact healthy", isOn: $overloadHealthy)
                    Button("Press START") {
                        controlStart = true
                        if controlStop && overloadHealthy { held = true }
                    }
                    .buttonStyle(.bordered)
                    Button("Press STOP") { controlStart = false; held = false }
                        .buttonStyle(.bordered)
                    Text(held && controlStop && overloadHealthy ? "Starter coil ENERGIZED · sealed in" : "Starter coil OFF")
                        .font(.headline)
                        .foregroundStyle(held && controlStop && overloadHealthy ? WorkbenchStyle.cyan : .orange)
                    Text("Simplified logical trainer only. Actual interlocking and safety circuits must not be simulated as production protection.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                }
                .workspaceCard()

                NavigationLink {
                    PracticeQuizView()
                } label: {
                    Label("Open native 25-question daily quiz", systemImage: "checklist")
                }
                NavigationLink {
                    PortalScreen(title: "Full calculators", path: "/calculators.html")
                } label: {
                    Label("Open the full web CEC reference and worksheets", systemImage: "book.pages")
                }
                Text("Ontario/OESC provincial amendments and ESA bulletins may modify the installation requirements. Verify the applicable adopted Code.")
                    .font(.caption).foregroundStyle(WorkbenchStyle.muted)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Learn")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct NativeFieldToolsView: View {
    @ObservedObject var store: NativeFieldRecordStore
    @State private var nameplateAsset = ""
    @State private var nameplateValues = ""
    @State private var commissionSite = ""
    @State private var checks = Set<String>()
    @State private var drawing = ""
    @State private var denominator = ""
    @State private var numerator = ""
    @State private var waste = "10"
    @State private var grounding = "Equipment bonding conductor"

    private let checklist = [
        "Correct drawing and equipment labels reviewed",
        "Isolation / lockout / no voltage verified by qualified person",
        "Terminations and tightening requirements checked",
        "Bonding and grounding paths verified",
        "Covers, guards, clearances and separation checked",
        "Testing / commissioning permit obtained",
        "Functional tests, deficiencies and handover documented"
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 13) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("NAMEPLATE & EQUIPMENT DATA")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workField("Equipment tag / model", $nameplateAsset)
                    workField("Voltage, FLA, kW/HP, PF, RPM, duty, notes", $nameplateValues)
                    Button("Save nameplate record") {
                        store.add(category: "nameplates", title: nameplateAsset, notes: nameplateValues)
                        nameplateAsset = ""; nameplateValues = ""
                    }
                    .disabled(nameplateAsset.isEmpty || nameplateValues.isEmpty)
                    .buttonStyle(.borderedProminent)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 9) {
                    Text("COMMISSIONING & INSPECTION").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workField("Site, circuit or work order", $commissionSite)
                    ForEach(checklist, id: \.self) { check in
                        Toggle(isOn: Binding(get: { checks.contains(check) },
                                             set: { enabled in
                            if enabled { checks.insert(check) }
                            else { checks.remove(check) }
                        })) {
                            Text(check).font(.subheadline)
                        }
                        .tint(WorkbenchStyle.cyan)
                    }
                    Button("Save commissioning checklist") {
                        let checked = checklist.map { (checks.contains($0) ? "[x] " : "[ ] ") + $0 }.joined(separator: "\n")
                        store.add(category: "commissioning", title: commissionSite, notes: checked)
                        checks.removeAll(); commissionSite = ""
                    }
                    .disabled(commissionSite.isEmpty)
                    .buttonStyle(.borderedProminent)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 9) {
                    Text("BLUEPRINT SCALE / MATERIAL ALLOWANCE")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workNumeric("Drawing length (mm)", $drawing)
                    workNumeric("Scale drawing units", $denominator)
                    workNumeric("Scale actual units", $numerator)
                    workNumeric("Route/waste allowance (%)", $waste)
                    if let length = Double(drawing), let d = Double(denominator),
                       let actualUnits = Double(numerator), let allowance = Double(waste),
                       d > 0, length >= 0, actualUnits > 0, allowance >= 0 {
                        let actual = length * actualUnits / d
                        Text(String(format: "Actual: %.1f mm · %.2f m\nWith allowance: %.1f mm", actual, actual / 1000, actual * (1 + allowance / 100)))
                            .font(.subheadline.monospacedDigit())
                    }
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 9) {
                    Text("GROUNDING & BONDING WIZARD")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    let choices = [
                        "Service grounding conductor","Equipment bonding conductor",
                        "Transformer secondary bonding jumper","Ground rod / plate",
                        "Grounding electrode ring","High-voltage station grounding grid"
                    ]
                    Picker("Element", selection: $grounding) {
                        ForEach(choices, id: \.self) { Text($0).tag($0) }
                    }
                    Text(grounding == "High-voltage station grounding grid"
                         ? "Specialized engineered touch/step potential and station-grid design; verify Section 10 and HV criteria."
                         : "Identify rating, OCPD, material, electrode or derived-system arrangement. Select the applicable CEC Section 10 sizing rule and table for this element.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
                .workspaceCard()

                NavigationLink {
                    OfflinePowerCalculatorsView()
                } label: {
                    Label("Fault-current, transformer and motor calculations", systemImage: "function")
                }
                NavigationLink {
                    TroubleshootingOfflineView()
                } label: {
                    Label("Offline industrial troubleshooting", systemImage: "wrench")
                }
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Field records")
    }
}

struct NativeProjectView: View {
    @ObservedObject var store: NativeFieldRecordStore
    @State private var projectName = ""
    @State private var client = ""
    @State private var location = ""
    @State private var system = "347/600 V 3φ"
    @State private var notes = ""
    @State private var takeoff = ""
    @State private var amps = ""
    @State private var lengthM = ""
    @State private var kFactor = "12.9"
    @State private var cm = ""
    @State private var voltage = "600"
    @State private var phase3 = true

    private var takeoffTotal: Double? {
        let lines = takeoff.split(separator: "\n")
        guard !lines.isEmpty else { return nil }
        var result = 0.0
        for line in lines {
            let parts = line.split(separator: ",", omittingEmptySubsequences: false)
            guard parts.count >= 3, let quantity = Double(parts[1].trimmingCharacters(in: .whitespaces)),
                  let cost = Double(parts[2].trimmingCharacters(in: .whitespaces)),
                  quantity >= 0, cost >= 0 else { return nil }
            result += quantity * cost
        }
        return result
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 13) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("OFFLINE PROJECT LOG").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workField("Project title", $projectName)
                    workField("Client / employer", $client)
                    workField("Location", $location)
                    workField("Electrical system", $system)
                    workField("Notes", $notes)
                    Button("Save project") {
                        store.add(category: "projects", title: projectName,
                                  notes: "Client: \(client); location: \(location); system: \(system)\n\(notes)")
                        projectName = ""; notes = ""
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(projectName.isEmpty)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("MATERIAL TAKEOFF").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Each line: description, quantity, unit cost (CAD)")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    TextField("Example: TECK90 cable,20,15.50", text: $takeoff, axis: .vertical)
                        .lineLimit(5...10)
                        .padding(11)
                        .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                    if let total = takeoffTotal {
                        Text("Estimated material cost: \(total, format: .currency(code: "CAD"))")
                            .font(.headline)
                            .foregroundStyle(WorkbenchStyle.cyan)
                    }
                    Button("Save takeoff") {
                        if let total = takeoffTotal {
                            store.add(category: "materials", title: "Material takeoff",
                                      notes: takeoff, amount: total)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(takeoffTotal == nil)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("CABLE PLANNING / VOLTAGE DROP")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workNumeric("Load current (A)", $amps)
                    workNumeric("One-way route length (m)", $lengthM)
                    workNumeric("Conductor K factor", $kFactor)
                    workNumeric("Circular mil area", $cm)
                    workNumeric("System voltage", $voltage)
                    Toggle("Three-phase", isOn: $phase3).tint(WorkbenchStyle.cyan)
                    if let current = Double(amps), let distance = Double(lengthM),
                       let k = Double(kFactor), let area = Double(cm), let v = Double(voltage),
                       current > 0, distance > 0, k > 0, area > 0, v > 0 {
                        let drop = (phase3 ? sqrt(3) : 2) * current * distance * 3.28084 * k / area
                        Text(String(format: "Estimated drop: %.2f V (%.2f%%). Verify AC impedance and CEC Table D3 notes.", drop, 100 * drop / v))
                            .font(.subheadline.monospacedDigit())
                    }
                }
                .workspaceCard()

                NavigationLink {
                    PortalScreen(title: "Cloud Field Suite", path: "/field-suite.html")
                } label: {
                    Label("Open advanced cloud project, quote and drawing tools", systemImage: "folder.badge.plus")
                }
                Text("Cloud Field Suite records are not automatically identical to these locally saved iOS records.")
                    .font(.caption).foregroundStyle(WorkbenchStyle.muted)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Projects")
    }
}

struct NativeCareerView: View {
    @ObservedObject var store: NativeFieldRecordStore
    @State private var employer = ""
    @State private var hours = ""
    @State private var category = "Construction / maintenance"
    @State private var credential = ""
    @State private var issuer = ""
    @State private var expiry = ""
    @State private var pay = ""
    @State private var regular = "40"
    @State private var ot = "0"
    @State private var multiplier = "1.5"
    @State private var weeks = "52"

    private var totalHours: Double {
        store.records.filter { $0.category == "hours" }.reduce(0) { $0 + $1.amount }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 13) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("TRADE / APPRENTICESHIP HOURS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text(String(format: "Locally recorded total: %.1f hours", totalHours))
                        .font(.headline).foregroundStyle(WorkbenchStyle.cyan)
                    workField("Employer / company", $employer)
                    workField("Work category", $category)
                    workNumeric("Hours", $hours)
                    Button("Record work hours") {
                        if let number = Double(hours), number > 0 {
                            store.add(category: "hours", title: employer,
                                      notes: category, amount: number)
                            hours = ""
                        }
                    }
                    .disabled(employer.isEmpty || (Double(hours) ?? 0) <= 0)
                    .buttonStyle(.borderedProminent)
                    Text("Personal record only; employer and Skilled Trades Ontario official records govern recognized hours.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("CERTIFICATE / CREDENTIAL WALLET")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workField("Training or credential", $credential)
                    workField("Issuer", $issuer)
                    workField("Expiry/renewal date", $expiry)
                    Button("Save credential") {
                        store.add(category: "credentials", title: credential,
                                  notes: "Issuer: \(issuer) · Expires: \(expiry)")
                        credential = ""
                    }
                    .disabled(credential.isEmpty)
                    .buttonStyle(.borderedProminent)
                }
                .workspaceCard()

                VStack(alignment: .leading, spacing: 10) {
                    Text("GROSS WAGE ESTIMATE")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    workNumeric("Hourly pay (CAD)", $pay)
                    workNumeric("Regular hours / week", $regular)
                    workNumeric("Overtime hours / week", $ot)
                    workNumeric("Overtime multiplier", $multiplier)
                    workNumeric("Weeks per year", $weeks)
                    if let pay = Double(pay), let reg = Double(regular), let ot = Double(ot),
                       let multiplier = Double(multiplier), let weeks = Double(weeks),
                       pay >= 0, reg >= 0, ot >= 0, multiplier > 0, weeks >= 0 {
                        let weekly = pay * (reg + multiplier * ot)
                        Text("Weekly gross: \(weekly, format: .currency(code: "CAD"))\nAnnualized gross: \(weekly * weeks, format: .currency(code: "CAD"))")
                            .font(.subheadline.monospacedDigit())
                    }
                    Text("Before deductions; statutory/contractual overtime rules vary.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                }
                .workspaceCard()
                VStack(alignment: .leading, spacing: 10) {
                    Text("OFFICIAL TRADE & JOB RESOURCES")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Link("Job Bank Canada", destination: URL(string: "https://www.jobbank.gc.ca/")!)
                    Link("Skilled Trades Ontario", destination: URL(string: "https://www.skilledtradesontario.ca/")!)
                    Link("Red Seal Program", destination: URL(string: "https://www.red-seal.ca/")!)
                }
                .workspaceCard()
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Career")
    }
}

private func workField(_ title: String, _ binding: Binding<String>) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(title).font(.subheadline.weight(.medium))
        TextField(title, text: binding)
            .padding(11)
            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
    }
}
private func workNumeric(_ title: String, _ binding: Binding<String>) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(title).font(.subheadline.weight(.medium))
        TextField(title, text: binding)
            .keyboardType(.decimalPad)
            .padding(11)
            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
    }
}
private struct WorkspaceCard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func workspaceCard() -> some View { modifier(WorkspaceCard()) }
}
