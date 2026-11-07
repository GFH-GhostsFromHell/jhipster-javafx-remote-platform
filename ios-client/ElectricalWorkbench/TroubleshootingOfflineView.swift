import SwiftUI
import Foundation

private struct DiagnosticTopic: Codable, Identifiable {
    let category: String
    let title: String
    let faultChecks: [String]
    let decision: String
    var id: String { category + ":" + title }
}

private struct DiagnosticCase: Codable, Identifiable {
    let id: UUID
    let topic: String
    let asset: String
    let notes: String
    let completed: Int
    let total: Int
    let createdAt: Date
}

private enum OfflineDiagnostics {
    static let universal = [
        "SAFETY: Confirm authorization, hazard assessment, all energy-source isolation, lockout/tagout and absence of voltage by an approved method.",
        "Interview the operator: identify symptoms, when the fault began, alarms and recent repairs.",
        "Review the OEM drawing, operating sequence, fault log, interlocks and expected device states.",
        "Divide source, protection, control supply, I/O, output and mechanical load into testable sections.",
        "Use a correctly rated instrument; know the expected result before each authorized test.",
        "Correct the verified cause without defeating safety or overload protection.",
        "Return to service only using approved tests, documentation and authorization."
    ]
    static let topics: [DiagnosticTopic] = {
        guard let url = Bundle.main.url(forResource: "diagnostic-guides", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let topics = try? JSONDecoder().decode([DiagnosticTopic].self, from: data) else {
            return []
        }
        return topics
    }()
}

struct TroubleshootingOfflineView: View {
    @State private var search = ""
    @State private var selectedID = ""
    @State private var completed: Set<Int> = []
    @State private var asset = ""
    @State private var notes = ""
    @State private var saved: [DiagnosticCase] = []
    @State private var savedMessage = ""

    private let storageKey = "ewb.ios.troubleshooting.cases.v1"

    private var filtered: [DiagnosticTopic] {
        OfflineDiagnostics.topics.filter {
            search.isEmpty ||
            $0.title.localizedCaseInsensitiveContains(search) ||
            $0.category.localizedCaseInsensitiveContains(search)
        }
    }

    private var selected: DiagnosticTopic? {
        OfflineDiagnostics.topics.first { $0.id == selectedID } ?? OfflineDiagnostics.topics.first
    }

    private var steps: [String] {
        guard let selected else { return [] }
        return Array(OfflineDiagnostics.universal.prefix(4)) +
               selected.faultChecks +
               Array(OfflineDiagnostics.universal.dropFirst(4))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("SAFETY-FIRST · OFFLINE KNOWLEDGE BASE")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("21 fault-isolation guides covering motors, PLCs, VFDs, mining equipment, SEL/GE protection, HVAC and fire alarm.")
                        .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)
                    Label("Never bypass safeties. Lockout/tagout and verify zero energy. HV and battery-electric repairs require OEM/site-qualified personnel.",
                          systemImage: "exclamationmark.shield")
                        .font(.caption)
                        .foregroundStyle(.orange)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .diagnosticCard()

                VStack(alignment: .leading, spacing: 10) {
                    TextField("Search faults, equipment, or category", text: $search)
                        .textInputAutocapitalization(.never)
                        .padding(11)
                        .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))
                    Picker("Select diagnostic fault", selection: $selectedID) {
                        ForEach(filtered) { guide in
                            Text(guide.category + " · " + guide.title).tag(guide.id)
                        }
                    }
                    .pickerStyle(.menu)
                    .onChange(of: selectedID) { _, _ in
                        completed.removeAll()
                        savedMessage = ""
                    }
                    if let selected {
                        Text(selected.title)
                            .font(.headline)
                        Text(selected.decision)
                            .font(.subheadline)
                            .foregroundStyle(WorkbenchStyle.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        Text("No guides found.")
                            .foregroundStyle(.orange)
                    }
                }
                .diagnosticCard()

                if selected != nil {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("CHECKLIST").font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                            Spacer()
                            Text("\(completed.count) of \(steps.count)")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(WorkbenchStyle.muted)
                        }
                        ForEach(steps.indices, id: \.self) { index in
                            Toggle(isOn: Binding(get: { completed.contains(index) },
                                                 set: { selected in
                                if selected { completed.insert(index) }
                                else { completed.remove(index) }
                            })) {
                                Text("\(index + 1). \(steps[index])")
                                    .font(.subheadline)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .toggleStyle(.switch)
                            .tint(WorkbenchStyle.cyan)
                            Divider().opacity(0.25)
                        }
                    }
                    .diagnosticCard()

                    VStack(alignment: .leading, spacing: 10) {
                        Text("SAVE A LOCAL TROUBLESHOOTING CASE")
                            .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        TextField("Asset, area, or work order", text: $asset)
                            .padding(10)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                        TextField("Symptoms, measured values, findings and resolution", text: $notes, axis: .vertical)
                            .lineLimit(3...7)
                            .padding(10)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                        Button {
                            saveCase()
                        } label: {
                            Label("Save case on this device", systemImage: "square.and.arrow.down")
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(WorkbenchStyle.cyan)
                        .foregroundStyle(WorkbenchStyle.background)
                        .disabled(asset.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        if !savedMessage.isEmpty {
                            Text(savedMessage).font(.caption).foregroundStyle(WorkbenchStyle.muted)
                        }
                    }
                    .diagnosticCard()
                }

                if !saved.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SAVED CASES • \(saved.count)")
                            .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                        ForEach(saved) { item in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.asset).font(.subheadline.bold())
                                Text(item.topic).font(.caption).foregroundStyle(WorkbenchStyle.muted)
                                Text(item.notes).font(.caption)
                                Text(item.createdAt, style: .date).font(.caption2)
                                HStack {
                                    ShareLink(item: caseSummary(item)) {
                                        Label("Share", systemImage: "square.and.arrow.up")
                                    }
                                    .font(.caption)
                                    Spacer()
                                    Button("Delete", role: .destructive) {
                                        saved.removeAll { $0.id == item.id }
                                        persist()
                                    }
                                    .font(.caption)
                                }
                            }
                            Divider()
                        }
                    }
                    .diagnosticCard()
                }
                Text("Saved cases stay on this device and are not synced to your employer or portal. Avoid sensitive personal information; verify all work using your site procedures and applicable legislation.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Troubleshooting")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if selectedID.isEmpty { selectedID = OfflineDiagnostics.topics.first?.id ?? "" }
            restore()
        }
    }

    private func saveCase() {
        guard let topic = selected else { return }
        let next = DiagnosticCase(id: UUID(), topic: topic.title,
                                  asset: asset.trimmingCharacters(in: .whitespacesAndNewlines),
                                  notes: notes,
                                  completed: completed.count,
                                  total: steps.count, createdAt: Date())
        saved.insert(next, at: 0)
        if saved.count > 100 { saved = Array(saved.prefix(100)) }
        persist()
        savedMessage = "Case saved locally. Share or delete using Saved Cases below."
    }

    private func caseSummary(_ item: DiagnosticCase) -> String {
        """
        Electrical Workbench troubleshooting
        Asset: \(item.asset)
        Fault: \(item.topic)
        Progress: \(item.completed) / \(item.total)
        Notes: \(item.notes)
        """
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(saved) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }

    private func restore() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let values = try? JSONDecoder().decode([DiagnosticCase].self, from: data) else { return }
        saved = values
    }
}

private struct DiagnosticCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func diagnosticCard() -> some View { modifier(DiagnosticCard()) }
}
