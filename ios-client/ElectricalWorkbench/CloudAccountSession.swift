import Foundation
import SwiftUI
import WebKit

/// Server-verified entitlement map; never infer feature access from the subscription label.
struct CloudIdentity: Codable {
    let username: String
    let displayName: String
    let email: String
    let tier: String
    let provider: String
    let features: [String: Bool]
    let verifiedAt: Date

    var featureCount: Int { features.values.filter { $0 }.count }
}

enum CloudError: LocalizedError {
    case invalidCredentials
    case disabled
    case unexpectedResponse
    case serviceUnavailable
    case api(String)

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: "Incorrect username/password, unverified account, or access has been disabled."
        case .disabled: "This Electrical Workbench account is disabled."
        case .unexpectedResponse: "The portal returned an unexpected response. Please try again."
        case .serviceUnavailable: "The cloud portal is unavailable. Try again with a connection."
        case .api(let detail): detail
        }
    }
}

enum PortalAPI {
    static let base = PortalEndpoint.root

    static func request(
        path: String,
        username: String,
        password: String,
        method: String = "GET",
        json: Data? = nil
    ) async throws -> Data {
        guard path.hasPrefix("/"), !path.hasPrefix("//"), !path.contains("://") else {
            throw CloudError.unexpectedResponse
        }
        var request = URLRequest(url: base.appendingPathComponent(String(path.dropFirst())))
        request.httpMethod = method
        request.timeoutInterval = 25
        let basic = Data("\(username):\(password)".utf8).base64EncodedString()
        request.setValue("Basic \(basic)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let json {
            request.httpBody = json
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw CloudError.unexpectedResponse
            }
            if http.statusCode == 401 || http.statusCode == 403 {
                throw CloudError.invalidCredentials
            }
            guard (200..<300).contains(http.statusCode) else {
                throw CloudError.api("Portal request failed (HTTP \(http.statusCode)).")
            }
            return data
        } catch let error as CloudError {
            throw error
        } catch {
            throw CloudError.serviceUnavailable
        }
    }
}

@MainActor
final class CloudAccountSession: ObservableObject {
    @Published private(set) var identity: CloudIdentity?
    @Published private(set) var isOffline = false
    @Published private(set) var busy = false
    @Published var error = ""
    @Published var webSessionWarning = ""

    private var password: String?
    private let cacheKey = "ewb.ios.verifiedIdentity.v1"

    var signedIn: Bool { identity != nil }
    var username: String { identity?.username ?? "Local user" }

    init() {
        // Deliberately don't restore cloud access silently. Only offer cached
        // short-lived permissions when the user explicitly selects offline mode.
    }

    func feature(_ name: String) -> Bool {
        guard let identity else { return false }
        if isOffline {
            // Permit recently verified basic/offline tools; premium features fail closed.
            let basic: Set<String> = ["ELECTRICAL_CALCULATORS", "RESIDENTIAL_REFERENCE", "DASHBOARD", "REPORTS"]
            return basic.contains(name) && identity.features[name] == true
        }
        return identity.features[name] == true
    }

    var canUseBasicTools: Bool {
        identity != nil && (!isOffline || identity?.features["ELECTRICAL_CALCULATORS"] == true)
    }

    func signIn(username: String, password: String) async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty && !password.isEmpty else {
            error = "Enter your Electrical Workbench username and password."
            return
        }
        busy = true
        error = ""
        defer { busy = false }
        do {
            let identity = try await verify(user, password: password)
            self.identity = identity
            self.password = password
            isOffline = false
            saveVerified(identity)
            webSessionWarning = ""
            do {
                try await establishWebSession(username: user, password: password)
            } catch {
                webSessionWarning = "Cloud tools may ask you to sign in separately. The native account and entitlements are verified."
            }
        } catch {
            self.identity = nil
            self.password = nil
            self.error = error.localizedDescription
        }
    }

    private func verify(_ username: String, password: String) async throws -> CloudIdentity {
        let data = try await PortalAPI.request(path: "/api/client/bootstrap", username: username, password: password)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let user = json["user"] as? [String: Any],
              let features = json["features"] as? [String: Bool] else {
            throw CloudError.unexpectedResponse
        }
        guard user["enabled"] as? Bool ?? true else { throw CloudError.disabled }
        return CloudIdentity(
            username: user["username"] as? String ?? username,
            displayName: user["displayName"] as? String ?? username,
            email: user["email"] as? String ?? "",
            tier: user["subscriptionTier"] as? String ?? "TIER_1",
            provider: user["loginProvider"] as? String ?? "PASSWORD",
            features: features,
            verifiedAt: .now
        )
    }

    func refresh() async {
        guard let name = identity?.username, let password, !isOffline, !busy else { return }
        do {
            let verified = try await verify(name, password: password)
            identity = verified
            saveVerified(verified)
        } catch CloudError.invalidCredentials {
            await signOut()
            error = "Your session is no longer authorized. Please sign in."
        } catch CloudError.disabled {
            await signOut()
            error = "Your account is no longer enabled."
        } catch {
            // Do not silently grant premium feature access from a failed refresh.
            if var cached = identity?.features {
                for key in cached.keys where key != "ELECTRICAL_CALCULATORS" && key != "RESIDENTIAL_REFERENCE" {
                    cached[key] = false
                }
                if let previous = identity {
                    identity = CloudIdentity(username: previous.username,
                        displayName: previous.displayName, email: previous.email,
                        tier: previous.tier, provider: previous.provider,
                        features: cached, verifiedAt: previous.verifiedAt)
                }
            }
            webSessionWarning = "Offline or cloud temporarily unavailable. Premium features require a successful account refresh."
        }
    }

    func useOfflineCache() {
        guard let data = UserDefaults.standard.data(forKey: cacheKey),
              let cached = try? JSONDecoder().decode(CloudIdentity.self, from: data),
              Date().timeIntervalSince(cached.verifiedAt) < 24 * 3600,
              cached.features["ELECTRICAL_CALCULATORS"] == true else {
            error = "Offline access requires a previously verified account used within 24 hours. Sign in while connected first."
            return
        }
        identity = cached
        isOffline = true
        password = nil
        error = ""
        webSessionWarning = "Offline mode · premium cloud functions and server entitlement checks are unavailable."
    }

    func signOut() async {
        identity = nil
        password = nil
        isOffline = false
        UserDefaults.standard.removeObject(forKey: cacheKey)
        let store = WKWebsiteDataStore.default().httpCookieStore
        let cookies = await withCheckedContinuation { continuation in
            store.getAllCookies { continuation.resume(returning: $0) }
        }
        for cookie in cookies where cookie.domain.contains("remote-javafx-portal.onrender.com") {
            await withCheckedContinuation { continuation in
                store.delete(cookie) { continuation.resume() }
            }
        }
    }

    func api(_ path: String, method: String = "GET", json: Data? = nil) async throws -> Data {
        guard let identity, let password, !isOffline else {
            throw CloudError.api("Sign in online to use this cloud feature.")
        }
        guard feature("DASHBOARD") || path.hasPrefix("/api/client") else {
            throw CloudError.api("Your account does not have portal access.")
        }
        return try await PortalAPI.request(path: path, username: identity.username,
                                           password: password, method: method, json: json)
    }

    private func saveVerified(_ identity: CloudIdentity) {
        if let data = try? JSONEncoder().encode(identity) {
            UserDefaults.standard.set(data, forKey: cacheKey)
        }
    }

    private func establishWebSession(username: String, password: String) async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieAcceptPolicy = .always
        configuration.httpShouldSetCookies = true
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        var req = URLRequest(url: PortalEndpoint.url(for: "/login"))
        req.httpMethod = "POST"
        req.timeoutInterval = 25
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var form = URLComponents()
        form.queryItems = [URLQueryItem(name: "username", value: username),
                           URLQueryItem(name: "password", value: password)]
        req.httpBody = (form.percentEncodedQuery ?? "").data(using: .utf8)
        let (_, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse,
              (200..<400).contains(http.statusCode) else { throw CloudError.invalidCredentials }
        let cookies = configuration.httpCookieStorage?.cookies?.filter {
            $0.domain.contains("remote-javafx-portal.onrender.com")
        } ?? []
        guard !cookies.isEmpty else { throw CloudError.unexpectedResponse }
        for cookie in cookies {
            await withCheckedContinuation { continuation in
                WKWebsiteDataStore.default().httpCookieStore.setCookie(cookie) {
                    continuation.resume()
                }
            }
        }
    }
}

struct CloudSignInView: View {
    @EnvironmentObject private var account: CloudAccountSession
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 9) {
                        Label("ELECTRICAL WORKBENCH", systemImage: "bolt.fill")
                            .font(.caption.bold())
                            .foregroundStyle(WorkbenchStyle.cyan)
                        Text("Sign in to your workbench")
                            .font(.title2.bold())
                        Text("Use your existing Android, desktop or web account. Your tier and feature overrides come directly from the same portal.")
                            .font(.subheadline)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 16))

                    VStack(alignment: .leading, spacing: 14) {
                        TextField("Username", text: $username)
                            .textContentType(.username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.next)
                            .padding(12)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))

                        SecureField("Password", text: $password)
                            .textContentType(.password)
                            .submitLabel(.go)
                            .onSubmit { Task { await account.signIn(username: username, password: password) } }
                            .padding(12)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))

                        if !account.error.isEmpty {
                            Label(account.error, systemImage: "exclamationmark.circle")
                                .font(.subheadline)
                                .foregroundStyle(.orange)
                        }

                        Button {
                            Task { await account.signIn(username: username, password: password) }
                        } label: {
                            if account.busy {
                                ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                            } else {
                                Label("Sign in", systemImage: "arrow.right.circle")
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(WorkbenchStyle.cyan)
                        .foregroundStyle(WorkbenchStyle.background)
                        .disabled(account.busy)

                        Button("Continue with previously verified offline account") {
                            account.useOfflineCache()
                        }
                        .font(.caption)
                        .frame(maxWidth: .infinity, minHeight: 44)

                        NavigationLink("Create account, verify email, or reset password") {
                            PortalScreen(title: "Account recovery", path: "/account.html")
                        }
                        .font(.subheadline)
                    }
                    .padding(16)
                    .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 16))

                    Text("Your password is used for this active app session, not saved to offline storage. A short-lived offline account cache stores no password; paid features are unavailable without online verification.")
                        .font(.footnote)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
                .padding(16)
                .frame(maxWidth: 600)
                .frame(maxWidth: .infinity)
            }
            .background(WorkbenchStyle.background)
            .navigationTitle("Account access")
        }
    }
}
