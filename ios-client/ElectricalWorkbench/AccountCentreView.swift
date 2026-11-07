import SwiftUI

private struct IOSReleaseManifest: Decodable {
    let platform: String
    let latestVersion: String
    let appStoreUrl: String
    let releaseNotes: String
}

@MainActor
final class IOSUpdateMonitor: ObservableObject {
    @Published private(set) var message = "Not checked yet"
    @Published private(set) var availableURL: URL?
    @Published private(set) var latest = ""
    @Published private(set) var checking = false

    private var lastCheck = Date.distantPast

    func checkIfDue(force: Bool = false) async {
        if checking || (!force && Date().timeIntervalSince(lastCheck) < 30 * 60) {
            return
        }
        checking = true
        lastCheck = Date()
        defer { checking = false }
        let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.2.0"
        guard let endpoint = URL(string: "https://remote-javafx-portal.onrender.com/ios-release.json") else {
            message = "No release feed configured."
            return
        }
        do {
            var request = URLRequest(url: endpoint)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            request.timeoutInterval = 12
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let status = response as? HTTPURLResponse, status.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }
            let manifest = try JSONDecoder().decode(IOSReleaseManifest.self, from: data)
            guard manifest.platform == "ios", !manifest.latestVersion.isEmpty else {
                throw URLError(.cannotParseResponse)
            }
            latest = manifest.latestVersion
            guard Self.isNewer(manifest.latestVersion, than: current) else {
                message = "This app is up to date (v\(current))."
                availableURL = nil
                return
            }
            if let url = URL(string: manifest.appStoreUrl),
               url.scheme == "https", url.host == "apps.apple.com" {
                availableURL = url
                message = "Version \(manifest.latestVersion) is available in the App Store."
            } else {
                availableURL = nil
                message = "Version \(manifest.latestVersion) is announced; an App Store link is not available yet."
            }
        } catch {
            availableURL = nil
            message = "Unable to check for updates. You can retry when online."
        }
    }

    private static func isNewer(_ remote: String, than local: String) -> Bool {
        let a = remote.split(separator: ".").map { Int($0) ?? 0 }
        let b = local.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0
            let y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}

struct AccountCentreView: View {
    @ObservedObject var updates: IOSUpdateMonitor

    var body: some View {
        List {
            Section {
                NavigationLink {
                    PortalScreen(title: "Sign in", path: "/")
                } label: {
                    Label("Sign in to Electrical Workbench", systemImage: "person.crop.circle.badge.checkmark")
                }
                NavigationLink {
                    PortalScreen(title: "My account & subscription", path: "/portal.html")
                } label: {
                    Label("Profile, tier and admin settings", systemImage: "person.text.rectangle")
                }
                NavigationLink {
                    PortalScreen(title: "Register or recover account", path: "/account.html")
                } label: {
                    Label("Create account / verify / reset password", systemImage: "key")
                }
                Text("Cloud sign-in shares cookies between the embedded workbench tools. If Google sign-in opens Safari, that browser session may not carry back to the in-app portal; use password sign-in if needed.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            } header: {
                Text("Account & subscription")
            }

            Section("App version & updates") {
                LabeledContent("Installed version",
                               value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "Unknown")
                if updates.checking {
                    ProgressView("Checking iOS release status")
                } else {
                    Text(updates.message)
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
                Button {
                    Task { await updates.checkIfDue(force: true) }
                } label: {
                    Label("Check for updates", systemImage: "arrow.clockwise")
                }
                .disabled(updates.checking)

                if let url = updates.availableURL {
                    Link(destination: url) {
                        Label("Open App Store update", systemImage: "arrow.up.right.square")
                    }
                }
                Text("iOS updates are installed via Apple's App Store or TestFlight, not downloaded as Android APKs.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }

            Section("Help & limitations") {
                NavigationLink {
                    PortalScreen(title: "Troubleshooting KB", path: "/troubleshooting-kb.html")
                } label: {
                    Label("Troubleshooting knowledge base", systemImage: "wrench.adjustable")
                }
                Text("Native bender, calibration, pull-box and practice quiz work offline. Other electrical worksheets, 3D models, subscriptions and cloud data use your portal account.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .background(WorkbenchStyle.background)
        .navigationTitle("Account")
        .task { await updates.checkIfDue() }
    }
}
