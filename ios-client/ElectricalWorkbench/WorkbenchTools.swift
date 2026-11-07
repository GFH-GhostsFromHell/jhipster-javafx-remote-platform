import SwiftUI

struct CloudTool: Hashable, Identifiable {
    let name: String
    let path: String
    let description: String
    let icon: String

    var id: String { path }
}

struct WorkbenchTools: View {
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
            Section("Conduit bending") {
                links(bending)
            }
            Section("Electrical calculations") {
                links(calculations)
            }
            Section("Cloud and help") {
                links(resources)
            }
            Section {
                Text("Web tools use your Electrical Workbench account and current subscription. They require an internet connection.")
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
                PortalScreen(title: entry.name, path: entry.path)
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
