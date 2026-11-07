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
    @EnvironmentObject private var account: CloudAccountSession
    @ObservedObject private var sync = AccountRecordSync.shared
    @ObservedObject var updates: IOSUpdateMonitor

    private var conflictBackup: String {
        let key = "ewb.ios.sync.\(account.username).conflicts"
        guard let data = UserDefaults.standard.data(forKey: key),
              let text = String(data: data, encoding: .utf8) else { return "" }
        return text
    }

    var body: some View {
        List {
            Section("Verified account") {
                if let identity = account.identity {
                    LabeledContent("Name", value: identity.displayName)
                    LabeledContent("Username", value: identity.username)
                    LabeledContent("Email", value: identity.email.isEmpty ? "Not set" : identity.email)
                    LabeledContent("Subscription", value: identity.tier.replacingOccurrences(of: "_", with: " "))
                    LabeledContent("Login provider", value: identity.provider)
                    LabeledContent("Enabled cloud features", value: "\(identity.featureCount)")
                    LabeledContent("Connection", value: account.isOffline ? "Verified offline cache" : "Signed in online")
                    if !account.webSessionWarning.isEmpty {
                        Text(account.webSessionWarning)
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                    Button {
                        Task { await account.refresh() }
                    } label: {
                        Label("Refresh account and permissions", systemImage: "arrow.clockwise")
                    }
                    .disabled(account.isOffline)
                    Button(role: .destructive) {
                        Task { await account.signOut() }
                    } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }

            Section {
                NavigationLink {
                    PortalScreen(title: "Cloud portal", path: "/portal.html")
                } label: {
                    Label("Open Electrical Workbench portal", systemImage: "cloud")
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
                NavigationLink {
                    EnterpriseTeamView()
                } label: {
                    Label("Business membership and team dashboard", systemImage: "person.3")
                }
                Text("Native password login verifies server entitlements. Google/OAuth sign-in in Safari may not share its session with the app. Team assignments and web subscription changes remain server-controlled.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            } header: {
                Text("Account & subscription")
            }

            Section("Cross-device account synchronization") {
                Label(sync.status, systemImage: sync.isSyncing ? "arrow.triangle.2.circlepath" : "checkmark.icloud")
                    .font(.subheadline)
                    .foregroundStyle(WorkbenchStyle.muted)
                Button {
                    Task { await sync.syncIfDue(account: account, force: true) }
                } label: {
                    Label("Synchronize saved records now", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(sync.isSyncing || account.isOffline)
                if !conflictBackup.isEmpty {
                    ShareLink(item: conflictBackup) {
                        Label("Export conflicting edits for review", systemImage: "square.and.arrow.up")
                    }
                    Text("Conflicts remain saved locally. The app will not silently overwrite a newer revision.")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
                Text("Projects, safety reports, saved bender profiles, quiz history, field and training records synchronize through your private account. Offline changes upload after reconnecting. Android and Windows use the same server.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
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
                Text("Native bender, safety, 309A quizzes, learning, power worksheets, projects and career records work in verified offline mode. ChatGPT, enterprise, advanced engineering, 3D models and cloud subscriptions need online authorization.")
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
