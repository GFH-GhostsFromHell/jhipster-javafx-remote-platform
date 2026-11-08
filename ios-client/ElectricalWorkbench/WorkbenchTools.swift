import SwiftUI

struct CloudTool: Hashable, Identifiable {
    let name: String
    let path: String
    let description: String
    let icon: String
    var feature: String? = nil

    var id: String { path }
}

struct WorkbenchTools: View {
    @EnvironmentObject private var account: CloudAccountSession

    private let bending = [
        CloudTool(name: "Advanced bend instructions", path: "/bend-instruction-lab.html",
                  description: "Marking diagrams, hook facing, and 3D", icon: "cube.transparent"),
        CloudTool(name: "Visual bending calculators", path: "/bend-calcs.html",
                  description: "Offsets, saddles, parallel runs, and bends", icon: "ruler"),
        CloudTool(name: "Bender calibration", path: "/bender-calibration.html",
                  description: "Measure and save shoe deduct or gain", icon: "slider.horizontal.3")
    ]
    private let calculations = [
        CloudTool(name: "Guided calculators", path: "/guided-calculators.html",
                  description: "Step-by-step electrical calculations", icon: "function"),
        CloudTool(name: "CEC calculators", path: "/calculators.html",
                  description: "Electrical code calculation workspace", icon: "number"),
        CloudTool(name: "Advanced calculations", path: "/advanced-calculations.html",
                  description: "More complex engineering worksheets", icon: "bolt.circle"),
        CloudTool(name: "Engineering power systems", path: "/engineering-power.html",
                  description: "Sequence components, per-unit, short-circuit and power-system tools",
                  icon: "bolt.horizontal.circle", feature: "ENGINEERING_POWER_SYSTEMS"),
        CloudTool(name: "Pull box sizing", path: "/pull-box-calcs.html",
                  description: "Straight, angle, and U-pull box dimensions", icon: "square.dashed"),
        CloudTool(name: "Field suite", path: "/field-suite.html",
                  description: "Field layout and reference tools", icon: "square.grid.2x2")
    ]
    private let resources = [
        CloudTool(name: "Troubleshooting knowledge base", path: "/troubleshooting-kb.html",
                  description: "Step-by-step electrical fault isolation", icon: "wrench.adjustable"),
        CloudTool(name: "My portal", path: "/portal.html",
                  description: "Cloud workspace and profile", icon: "cloud"),
        CloudTool(name: "Register or recover account", path: "/account.html",
                  description: "Email verification and password recovery", icon: "person.crop.circle")
    ]

    var body: some View {
        List {
            Section("Offline field calculators") {
                NavigationLink {
                    FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: "Circuit + Conductor Review") {
                        UnifiedCircuitSizingView()
                    }
                } label: {
                    Label("Circuit + conductor sizing · offline", systemImage: "bolt.circle")
                }
                NavigationLink {
                    QuickBenderView()
                } label: {
                    Label("Conduit bending · all common bends", systemImage: "ruler")
                }
                NavigationLink {
                    BenderCalibrationView()
                } label: {
                    Label("Bender calibration · save shoe", systemImage: "slider.horizontal.3")
                }
                NavigationLink {
                    PullBoxSizingView()
                } label: {
                    Label("Pull-box sizing · straight / angle / U", systemImage: "square.dashed")
                }
                NavigationLink {
                    FabricationView()
                } label: {
                    Label("Bend gain, cut length and parallel racks", systemImage: "square.stack.3d.up")
                }
                NavigationLink {
                    TroubleshootingOfflineView()
                } label: {
                    Label("Troubleshooting · 21 guides and work orders", systemImage: "wrench.and.screwdriver")
                }
                NavigationLink {
                    PracticeQuizView()
                } label: {
                    Label("Daily 309A quiz · 25 questions", systemImage: "checklist")
                }
            }
            Section("Native electrician workspaces") {
                NavigationLink {
                    FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: "Electrical calculators") {
                        OfflinePowerCalculatorsView()
                    }
                } label: {
                    Label("Electrical power formulas · offline", systemImage: "function")
                }
                NavigationLink {
                    NativeSafetyWorkspaceView()
                } label: {
                    Label("Electrical safety · inspections and records", systemImage: "shield.lefthalf.filled")
                }
                NavigationLink {
                    NativeWorkspacesView()
                } label: {
                    Label("Learn, field, projects and career", systemImage: "folder")
                }
                NavigationLink {
                    FeatureGate(feature: "ASK_CHATGPT", title: "Ask ChatGPT") {
                        NativeChatGPTWorkspaceView()
                    }
                } label: {
                    Label("Ask ChatGPT · connected account", systemImage: "bubble.left.and.text.bubble.right")
                }
                NavigationLink {
                    EnterpriseTeamView()
                } label: {
                    Label("Enterprise seats and membership", systemImage: "person.3")
                }
            }
            Section("Conduit bending · web + 3D") {
                links(bending)
            }
            Section("Electrical calculations") {
                links(calculations)
            }
            Section("Field workspaces") {
                NavigationLink {
                    PortalScreen(title: "Field workspaces", path: "/field-suite.html")
                } label: {
                    Label("Projects, quotes, takeoffs and control simulator", systemImage: "folder")
                }
                NavigationLink {
                    PortalScreen(title: "Code news", path: "/news.html")
                } label: {
                    Label("Code news and bulletins", systemImage: "newspaper")
                }
            }
            Section("Cloud and help") {
                links(resources)
            }
            Section {
                Text("Cloud tiers and overrides are verified against the same account as Android. Some native tools have independent local records, not synchronized project history. Google/OAuth login remains browser-based.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .background(WorkbenchStyle.background)
        .navigationTitle("Tools")
    }

    private func links(_ entries: [CloudTool]) -> some View {
        ForEach(entries) { entry in
            NavigationLink {
                if let required = entry.feature {
                    FeatureGate(feature: required, title: entry.name) {
                        PortalScreen(title: entry.name, path: entry.path)
                    }
                } else {
                    PortalScreen(title: entry.name, path: entry.path)
                }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: entry.icon)
                        .frame(width: 27)
                        .foregroundStyle(WorkbenchStyle.cyan)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(entry.name).font(.subheadline.weight(.semibold))
                        Text(entry.description)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }
}
