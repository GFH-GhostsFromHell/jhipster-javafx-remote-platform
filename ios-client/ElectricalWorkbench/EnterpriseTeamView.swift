import SwiftUI
import Foundation

private struct EnterpriseDashboard: Decodable {
    let businessName: String?
    let ownerUsername: String?
    let seatCount: Int?
    let tier1Count: Int?
    let tier2Count: Int?
    let tier3Count: Int?
}
private struct EnterpriseMembership: Decodable {
    let managed: Bool
    let businessName: String?
    let ownerUsername: String?
    let assignedTier: String?
    let roleLabel: String?
}

struct EnterpriseTeamView: View {
    @EnvironmentObject private var account: CloudAccountSession
    @State private var dashboard: EnterpriseDashboard?
    @State private var membership: EnterpriseMembership?
    @State private var busy = false
    @State private var error = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("BUSINESS / ENTERPRISE")
                        .font(.caption.bold())
                        .foregroundStyle(WorkbenchStyle.cyan)
                    Text("The same business/team account and server-controlled seat allocations used on Android.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                    if busy { ProgressView("Checking business membership…") }
                    if !error.isEmpty {
                        Label(error, systemImage: "info.circle")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                    }
                    Button("Refresh membership") {
                        Task { await load() }
                    }
                    .disabled(busy || account.isOffline)
                    .font(.subheadline.weight(.semibold))
                }
                .enterpriseCard()

                if let membership, membership.managed {
                    VStack(alignment: .leading, spacing: 7) {
                        Text("BUSINESS-MANAGED ACCOUNT")
                            .font(.caption.bold())
                            .foregroundStyle(WorkbenchStyle.cyan)
                        Text(membership.businessName ?? "Business Team").font(.headline)
                        Text("Assigned tier: \(membership.assignedTier ?? "Unknown")")
                        if let role = membership.roleLabel, !role.isEmpty {
                            Text("Role: \(role)")
                        }
                        Text("Your company controls your assigned subscription seat.")
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                    .enterpriseCard()
                }

                if let dashboard, account.feature("ENTERPRISE_TEAM_MANAGEMENT") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(dashboard.businessName ?? "Business Team")
                            .font(.headline)
                        Text("Owner: \(dashboard.ownerUsername ?? "Unknown")")
                            .font(.subheadline)
                            .foregroundStyle(WorkbenchStyle.muted)
                        enterpriseStat("Seats", dashboard.seatCount)
                        enterpriseStat("Tier 1", dashboard.tier1Count)
                        enterpriseStat("Tier 2", dashboard.tier2Count)
                        enterpriseStat("Tier 3", dashboard.tier3Count)
                        NavigationLink {
                            PortalScreen(title: "Business Team Manager", path: "/portal.html#enterprise")
                        } label: {
                            Label("Manage team members and seats", systemImage: "person.3")
                        }
                        .font(.subheadline.weight(.semibold))
                        Text("Team mutations remain on the server and require the connected business owner's permissions.")
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                    .enterpriseCard()
                } else if !account.feature("ENTERPRISE_TEAM_MANAGEMENT") {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Team administration requires a verified Business / Tier 4 account, or an explicit server entitlement.",
                              systemImage: "lock.shield")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                        Text("This account cannot manage other users. Any assigned business membership above is read-only.")
                            .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                    }
                    .enterpriseCard()
                }
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Business Team")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func enterpriseStat(_ title: String, _ number: Int?) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(number.map(String.init) ?? "—")
                .font(.headline.monospacedDigit())
                .foregroundStyle(WorkbenchStyle.cyan)
        }
        .font(.subheadline)
    }

    private func load() async {
        guard account.signedIn && !account.isOffline && !busy else { return }
        busy = true
        error = ""
        defer { busy = false }
        do {
            let data = try await account.api("/api/enterprise/membership")
            membership = try JSONDecoder().decode(EnterpriseMembership.self, from: data)
        } catch {
            self.error = "Business membership could not be verified: " + error.localizedDescription
        }
        guard account.feature("ENTERPRISE_TEAM_MANAGEMENT") else { return }
        do {
            let data = try await account.api("/api/enterprise")
            dashboard = try JSONDecoder().decode(EnterpriseDashboard.self, from: data)
        } catch {
            self.error = "Business team could not be loaded: " + error.localizedDescription
        }
    }
}

private struct EnterpriseCard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func enterpriseCard() -> some View { modifier(EnterpriseCard()) }
}

struct FeatureGate<Content: View>: View {
    @EnvironmentObject private var account: CloudAccountSession
    let feature: String
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        Group {
            if account.feature(feature) {
                content()
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    Label(title + " is not enabled for this signed-in account.",
                          systemImage: "lock.shield")
                        .font(.headline)
                        .foregroundStyle(.orange)
                    Text("Connect to the cloud and refresh your server entitlements. This feature cannot be opened from an expired/offline permission cache or by changing a local tier label.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                    Button("Refresh permissions") {
                        Task { await account.refresh() }
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(18)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(WorkbenchStyle.background)
                .navigationTitle(title)
            }
        }
    }
}
