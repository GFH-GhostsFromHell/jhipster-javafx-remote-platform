import SwiftUI

private struct ElectricalTool: Identifiable {
    let id: String
    let name: String
    let description: String
    let category: String
    let terms: String
    let icon: String
    let path: String?
}

struct WorkbenchTools: View {
    @EnvironmentObject private var account: CloudAccountSession
    @State private var searchText = ""
    @State private var category = "All tools"
    @State private var favourites: Set<String> = []
    @State private var recent: [String] = []

    private let categories = ["All tools", "Installation", "Calculations", "Power & Protection", "Troubleshooting", "Projects", "Study & Safety"]

    // Stable identifiers match the web tool catalog and Android where tools overlap.
    private let tools: [ElectricalTool] = [
        .init(id: "conduit-marks", name: "Conduit Bend Marks + 3D", description: "Bend marks, shoe facing and 3D preview", category: "Installation", terms: "conduit offset saddle 90 kick pipe hook", icon: "cube.transparent", path: "/bend-instruction-lab.html"),
        .init(id: "bend-guide", name: "Conduit Bend Diagrams", description: "Offsets, saddles and parallel conduit", category: "Installation", terms: "emt pipe bending", icon: "ruler", path: "/bend-calcs.html"),
        .init(id: "bender-calibrate", name: "Calibrate a Conduit Bender", description: "Save your shoe deduct and gain", category: "Installation", terms: "shoe gain calibration", icon: "slider.horizontal.3", path: nil),
        .init(id: "quick-bender", name: "Quick Bend Marks - Offline", description: "Fast conduit marks without internet", category: "Installation", terms: "offset saddle 90", icon: "ruler", path: nil),
        .init(id: "fabrication", name: "Conduit Cut Length & Racks - Offline", description: "Parallel runs, gain and cut length", category: "Installation", terms: "fabrication rack", icon: "square.stack.3d.up", path: nil),
        .init(id: "pull-box", name: "Pull Box Dimensions - Offline", description: "Straight, angle and U-pull boxes", category: "Installation", terms: "junction box wall", icon: "square.dashed", path: nil),
        .init(id: "conductor-drop", name: "Circuit Sizing & Voltage Drop - Offline", description: "Conductor selection, ampacity and voltage drop", category: "Calculations", terms: "cable current table d3 wire", icon: "bolt.circle", path: nil),
        .init(id: "electrical-offline", name: "Electrical Calculators - Offline", description: "Power, motor, transformer and voltage drop", category: "Calculations", terms: "basic formulas kw kva", icon: "function", path: nil),
        .init(id: "guided", name: "Step-by-Step Calculators", description: "Guided inputs and worked electrical solutions", category: "Calculations", terms: "cec conductor transformer motor grounding", icon: "list.number", path: "/guided-calculators.html"),
        .init(id: "service-demand", name: "Service Load Calculations", description: "Hospital, apartment, dwelling and school", category: "Calculations", terms: "demand feeder residential hotel", icon: "building.2", path: "/guided-calculators.html?tool=service"),
        .init(id: "transformer", name: "Transformer Sizing", description: "Current, ratio and protection worksheets", category: "Calculations", terms: "primary secondary kva", icon: "bolt", path: "/guided-calculators.html?tool=xfmr"),
        .init(id: "motor", name: "Motor Protection & Starter Sizing", description: "Motor fuses, breakers and overloads", category: "Calculations", terms: "flc ocpd starter", icon: "gearshape", path: "/calculators.html?section=s28"),
        .init(id: "advanced-code", name: "Advanced CEC Calculators", description: "Detailed Code calculation worksheets", category: "Calculations", terms: "ground neutral fire pump hvac", icon: "function", path: "/advanced-calculations.html"),
        .init(id: "cec-sections", name: "Electrical Calculators by CEC Section", description: "Browse Code-based calculation topics", category: "Calculations", terms: "cec rule table", icon: "number", path: "/calculators.html"),
        .init(id: "relay", name: "Protection & Relay Calculators - Offline", description: "CT/PT, relay pickup and instrument scaling", category: "Power & Protection", terms: "ct pt 4-20ma current transformer", icon: "waveform.path.ecg", path: nil),
        .init(id: "power", name: "Power System Analysis", description: "Per-unit and short-circuit studies", category: "Power & Protection", terms: "sequence components faults", icon: "bolt.horizontal.circle", path: "/engineering-power.html"),
        .init(id: "fault", name: "Equipment Troubleshooting - Offline", description: "Fault diagnosis and work orders", category: "Troubleshooting", terms: "vfd plc motor mining no start", icon: "wrench.and.screwdriver", path: nil),
        .init(id: "troubleshooting-web", name: "Equipment Troubleshooting Guides", description: "Detailed guided diagnostic procedures", category: "Troubleshooting", terms: "step by step faults", icon: "wrench.adjustable", path: "/troubleshooting-kb.html"),
        .init(id: "jobsite", name: "Jobsite Tools & Records", description: "Inspections, nameplates, commissioning and projects", category: "Projects", terms: "field inspection", icon: "folder", path: nil),
        .init(id: "quote", name: "Build a Job Quote", description: "Materials, labour, taxes and margins", category: "Projects", terms: "estimating contractor", icon: "doc.plaintext", path: "/field-suite.html#quote"),
        .init(id: "saved", name: "Saved Cloud Work", description: "Account-synced records and exports", category: "Projects", terms: "projects backup sync", icon: "icloud", path: "/account-records.html"),
        .init(id: "quiz", name: "Red Seal Daily Quiz - Offline", description: "25 practice questions with explanations", category: "Study & Safety", terms: "309a study exam practice", icon: "checklist", path: nil),
        .init(id: "safety", name: "Electrical Safety", description: "Site inspection and hazard records", category: "Study & Safety", terms: "ppe loto approach", icon: "shield.lefthalf.filled", path: nil),
        .init(id: "code-updates", name: "Code Updates & Bulletins", description: "Ontario CEC/OESC notices", category: "Study & Safety", terms: "esa news", icon: "newspaper", path: "/news.html"),
        .init(id: "ask-chatgpt", name: "Ask ChatGPT", description: "Connect your eligible ChatGPT account", category: "Study & Safety", terms: "assistant chat ai", icon: "bubble.left.and.text.bubble.right", path: nil)
    ]

    private var prefix: String { "ewb.ios.toolShortcuts.v1." + account.username }
    private var matched: [ElectricalTool] {
        let words = searchText.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        return tools.filter { tool in
            (category == "All tools" || category == tool.category) &&
            words.allSatisfy { term in
                (tool.name + " " + tool.description + " " + tool.terms).lowercased().contains(term)
            }
        }.sorted {
            let lf = favourites.contains($0.id), rf = favourites.contains($1.id)
            if lf != rf { return lf }
            return $0.name < $1.name
        }
    }
    private var pinned: [ElectricalTool] {
        tools.filter { favourites.contains($0.id) }.sorted { $0.name < $1.name }
    }
    private var recentTools: [ElectricalTool] {
        recent.compactMap { id in tools.first { $0.id == id } }.filter { !favourites.contains($0.id) }.prefix(4).map { $0 }
    }

    var body: some View {
        List {
            Section {
                Text("1 · Find the task   2 · Enter values   3 · Verify the result   4 · Save or export")
                    .font(.subheadline.weight(.medium))
                Picker("Tool category", selection: $category) {
                    ForEach(categories, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.menu)
            } header: {
                Text("Start a workflow")
            }
            if searchText.isEmpty && category == "All tools" {
                Section("★ Favourites") {
                    if pinned.isEmpty {
                        Text("Tap the star beside a tool to pin it here.")
                            .foregroundStyle(WorkbenchStyle.muted)
                    } else {
                        ForEach(pinned) { taskRow($0) }
                    }
                }
                if !recentTools.isEmpty {
                    Section {
                        ForEach(recentTools) { taskRow($0) }
                        Button("Clear recent tools") {
                            recent = []
                            UserDefaults.standard.removeObject(forKey: prefix + ".recent")
                        }
                    } header: { Text("Recently opened") }
                }
            }
            Section(searchText.isEmpty ? "All available tools" : "Search results") {
                if matched.isEmpty {
                    Text("No matching tools. Try another word or select All tools.")
                } else {
                    ForEach(matched.filter { tool in !searchText.isEmpty || category != "All tools" || (!favourites.contains(tool.id) && !recentTools.contains(where: { recentTool in recentTool.id == tool.id })) }) { tool in taskRow(tool) }
                }
            }
            Section {
                Text("Stars and recent-tool shortcuts are saved on this device for the current account. They do not synchronize project records. Confirm the applicable CEC/OESC and equipment data before field use.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
        }
        .searchable(text: $searchText, prompt: "Motor fuse, offset bend, voltage drop")
        .scrollContentBackground(.hidden)
        .background(WorkbenchStyle.background)
        .navigationTitle("Find a Tool")
        .onAppear(perform: loadPreferences)
        .onChange(of: account.username) { _, _ in loadPreferences() }
    }

    @ViewBuilder
    private func taskRow(_ tool: ElectricalTool) -> some View {
        HStack(spacing: 8) {
            NavigationLink {
                destination(for: tool).onAppear { markOpened(tool.id) }
            } label: {
                HStack(spacing: 9) {
                    Image(systemName: tool.icon)
                        .frame(width: 22)
                        .foregroundStyle(WorkbenchStyle.cyan)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(tool.name).font(.subheadline.weight(.semibold))
                        Text(tool.description).font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                }
                .padding(.vertical, 5)
            }
            Button {
                toggleFavourite(tool.id)
            } label: {
                Image(systemName: favourites.contains(tool.id) ? "star.fill" : "star")
                    .foregroundStyle(favourites.contains(tool.id) ? .yellow : WorkbenchStyle.muted)
                    .frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(favourites.contains(tool.id) ? "Remove \(tool.name) from favourites" : "Add \(tool.name) to favourites")
        }
    }

    @ViewBuilder
    private func destination(for tool: ElectricalTool) -> some View {
        switch tool.id {
        case "quick-bender": QuickBenderView()
        case "bender-calibrate": BenderCalibrationView()
        case "fabrication": FabricationView()
        case "pull-box": PullBoxSizingView()
        case "conductor-drop":
            FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: tool.name) { UnifiedCircuitSizingView() }
        case "electrical-offline":
            FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: tool.name) { OfflinePowerCalculatorsView() }
        case "relay":
            FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: tool.name) { IndustrialFieldView() }
        case "fault": TroubleshootingOfflineView()
        case "jobsite": NativeWorkspacesView()
        case "quiz": PracticeQuizView()
        case "safety": NativeSafetyWorkspaceView()
        case "ask-chatgpt":
            FeatureGate(feature: "ASK_CHATGPT", title: tool.name) { NativeChatGPTWorkspaceView() }
        case "power":
            FeatureGate(feature: "ENGINEERING_POWER_SYSTEMS", title: tool.name) {
                PortalScreen(title: tool.name, path: tool.path ?? "/engineering-power.html")
            }
        default:
            PortalScreen(title: tool.name, path: tool.path ?? "/portal.html")
        }
    }

    private func loadPreferences() {
        favourites = Set(UserDefaults.standard.stringArray(forKey: prefix + ".favourites") ?? [])
        recent = UserDefaults.standard.stringArray(forKey: prefix + ".recent") ?? []
    }

    private func toggleFavourite(_ id: String) {
        if favourites.contains(id) { favourites.remove(id) } else { favourites.insert(id) }
        UserDefaults.standard.set(Array(favourites), forKey: prefix + ".favourites")
    }

    private func markOpened(_ id: String) {
        recent = [id] + recent.filter { $0 != id }
        recent = Array(recent.prefix(8))
        UserDefaults.standard.set(recent, forKey: prefix + ".recent")
    }
}
