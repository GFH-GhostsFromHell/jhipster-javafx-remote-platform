import SwiftUI

struct WorkbenchRoot: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var updates = IOSUpdateMonitor()
    @StateObject private var account = CloudAccountSession()

    var body: some View {
        Group {
            if account.signedIn {
                signedInContent
            } else {
                CloudSignInView()
            }
        }
        .environmentObject(account)
    }

    private var signedInContent: some View {
        TabView {
            NavigationStack {
                WorkbenchHome()
            }
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }

            NavigationStack {
                QuickBenderView()
            }
            .tabItem {
                Label("Bender", systemImage: "ruler")
            }

            NavigationStack {
                WorkbenchTools()
            }
            .tabItem {
                Label("Tools", systemImage: "square.grid.2x2.fill")
            }

            NavigationStack {
                NativeSafetyWorkspaceView()
            }
            .tabItem {
                Label("Safety", systemImage: "shield.lefthalf.filled")
            }

            NavigationStack {
                AccountCentreView(updates: updates)
            }
            .tabItem {
                Label("Account", systemImage: "person.crop.circle")
            }
        }
        .toolbarBackground(WorkbenchStyle.background, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .safeAreaInset(edge: .top) {
            if let url = updates.availableURL {
                Link(destination: url) {
                    Label("New iOS version available · Open App Store", systemImage: "arrow.down.circle.fill")
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(9)
                        .background(WorkbenchStyle.surface)
                }
            }
        }
        .task {
            while !Task.isCancelled {
                await updates.checkIfDue()
                await account.refresh()
                await AccountRecordSync.shared.syncIfDue(account: account)
                try? await Task.sleep(nanoseconds: 1_800_000_000_000)
            }
        }
        .onChange(of: scenePhase) { _, next in
            if next == .active {
                Task {
                    await updates.checkIfDue()
                    await account.refresh()
                    await AccountRecordSync.shared.syncIfDue(account: account)
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .ewbLocalRecordChanged)) { _ in
            Task { await AccountRecordSync.shared.syncIfDue(account: account, force: true) }
        }
    }
}

struct WorkbenchHome: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 9) {
                    Label("ELECTRICAL WORKBENCH", systemImage: "bolt.fill")
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(WorkbenchStyle.cyan)
                    Text("What do you need to do today?")
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                    Text("Start with a field task, or open Tools for electrical sizing, troubleshooting, projects and study. Cloud tools use your Electrical Workbench account.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 17))

                Text("QUICK FIELD TASKS").font(.caption.weight(.bold)).foregroundStyle(WorkbenchStyle.muted)
                NavigationLink {
                    QuickBenderView()
                } label: {
                    HomeDestination(
                        icon: "ruler",
                        title: "Conduit Bend Marks",
                        detail: "Offline offsets, rolling offsets, and 90° stub marks",
                        emphasis: true
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PortalScreen(title: "Advanced conduit bender", path: "/bend-instruction-lab.html")
                } label: {
                    HomeDestination(
                        icon: "cube.transparent",
                        title: "Conduit Bends + 3D Preview",
                        detail: "All bend types, diagrams, hook directions, and a rotatable result"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    TroubleshootingOfflineView()
                } label: {
                    HomeDestination(
                        icon: "wrench.and.screwdriver",
                        title: "Equipment Troubleshooting · Offline",
                        detail: "21 motor, PLC, VFD, mining and protection guides"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    FabricationView()
                } label: {
                    HomeDestination(
                        icon: "square.stack.3d.up",
                        title: "Conduit Cut Length & Rack Layout",
                        detail: "Offline bend gain, cut length, and parallel rack marks"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PracticeQuizView()
                } label: {
                    HomeDestination(
                        icon: "checklist",
                        title: "Red Seal Daily Quiz · 25 Questions",
                        detail: "Five categories, worked answers, offline study"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    BenderCalibrationView()
                } label: {
                    HomeDestination(
                        icon: "slider.horizontal.3",
                        title: "Calibrate a Conduit Bender",
                        detail: "Measure your shoe deduct and gain with scrap"
                    )
                }
                .buttonStyle(.plain)

                Text("SIZING, PROJECTS & STUDY").font(.caption.weight(.bold)).foregroundStyle(WorkbenchStyle.muted)
                NavigationLink {
                    PullBoxSizingView()
                } label: {
                    HomeDestination(
                        icon: "square.dashed",
                        title: "Pull Box Dimensions",
                        detail: "Straight, angle, U, mixed, and back-wall pulls"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    FeatureGate(feature: "ELECTRICAL_CALCULATORS", title: "Electrical calculators") {
                        OfflinePowerCalculatorsView()
                    }
                } label: {
                    HomeDestination(
                        icon: "function",
                        title: "Electrical Calculators · Offline",
                        detail: "Transformer, motor, voltage drop, power factor and more"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    NativeWorkspacesView()
                } label: {
                    HomeDestination(
                        icon: "folder",
                        title: "Jobsite Tools & Saved Projects",
                        detail: "Learn, field records, projects, career hours and credentials"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    FeatureGate(feature: "ASK_CHATGPT", title: "Ask ChatGPT") {
                        NativeChatGPTWorkspaceView()
                    }
                } label: {
                    HomeDestination(
                        icon: "bubble.left.and.text.bubble.right",
                        title: "Ask ChatGPT",
                        detail: "Use the verified ChatGPT portal connection"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PortalScreen(title: "Step-by-Step Calculators", path: "/guided-calculators.html")
                } label: {
                    HomeDestination(
                        icon: "function",
                        title: "Step-by-Step Calculators",
                        detail: "Service, transformer, motor, and other electrical calculations"
                    )
                }
                .buttonStyle(.plain)

                Text("Signed-in offline basics use a verified 24-hour cache. Premium calculations, cloud workspaces, enterprise and ChatGPT require live authorization. Field records stay on this device; Code and manufacturer verification still apply.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
                    .padding(.top, 4)
            }
            .padding(16)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Workbench")
    }
}

private struct HomeDestination: View {
    let icon: String
    let title: String
    let detail: String
    var emphasis = false

    var body: some View {
        HStack(alignment: .center, spacing: 13) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(WorkbenchStyle.cyan)
                .frame(width: 36)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.headline).foregroundStyle(.white)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(WorkbenchStyle.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.bold))
                .foregroundStyle(WorkbenchStyle.cyan)
                .accessibilityHidden(true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(emphasis ? WorkbenchStyle.surface : WorkbenchStyle.surface.opacity(0.8),
                    in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}
