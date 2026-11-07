import SwiftUI

struct WorkbenchRoot: View {
    var body: some View {
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
                AccountCentreView()
            }
            .tabItem {
                Label("Account", systemImage: "person.crop.circle")
            }
        }
        .toolbarBackground(WorkbenchStyle.background, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
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
                    Text("Electrician tools for the field")
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                    Text("Quick measurements and bend marks on your iPhone, with the rest of your cloud tools in one place.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 17))

                NavigationLink {
                    QuickBenderView()
                } label: {
                    HomeDestination(
                        icon: "ruler",
                        title: "Quick conduit bender",
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
                        title: "Advanced bender + 3D",
                        detail: "All bend types, diagrams, hook directions, and a rotatable result"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    TroubleshootingOfflineView()
                } label: {
                    HomeDestination(
                        icon: "wrench.and.screwdriver",
                        title: "Offline troubleshooting",
                        detail: "21 motor, PLC, VFD, mining and protection guides"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    FabricationView()
                } label: {
                    HomeDestination(
                        icon: "square.stack.3d.up",
                        title: "Conduit fabrication",
                        detail: "Offline bend gain, cut length, and parallel rack marks"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PracticeQuizView()
                } label: {
                    HomeDestination(
                        icon: "checklist",
                        title: "Daily 309A quiz · 25 questions",
                        detail: "Five categories, worked answers, offline study"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    BenderCalibrationView()
                } label: {
                    HomeDestination(
                        icon: "slider.horizontal.3",
                        title: "Save bender calibration",
                        detail: "Measure your shoe deduct and gain with scrap"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PullBoxSizingView()
                } label: {
                    HomeDestination(
                        icon: "square.dashed",
                        title: "Pull-box sizing",
                        detail: "Straight, angle, U, mixed, and back-wall pulls"
                    )
                }
                .buttonStyle(.plain)

                NavigationLink {
                    PortalScreen(title: "Guided CEC calculators", path: "/guided-calculators.html")
                } label: {
                    HomeDestination(
                        icon: "function",
                        title: "CEC calculators",
                        detail: "Service, transformer, motor, and other electrical calculations"
                    )
                }
                .buttonStyle(.plain)

                Text("Native bending, bender calibration, pull-box sizing and daily quizzes work offline. Cloud CEC worksheets and 3D preview require a connection. Verify job and code conditions before fabrication.")
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
