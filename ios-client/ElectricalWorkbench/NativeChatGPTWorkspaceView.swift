import SwiftUI
import Foundation

private struct AIChatLine: Codable, Identifiable {
    let id: UUID
    let role: String
    let text: String

    init(role: String, text: String) {
        id = UUID()
        self.role = role
        self.text = text
    }
}
private struct AIModel: Codable, Identifiable {
    let slug: String
    let displayName: String
    var id: String { slug }
}
private struct AIModelResponse: Decodable {
    let models: [AIModel]
}
private struct AIStatus: Decodable {
    let configured: Bool
    let planSharingConfigured: Bool
    let connected: Bool
    let sharingEnabled: Bool
    let name: String
    let email: String
    let message: String
}
private struct AIResponse: Decodable {
    let text: String
}
private struct AIRequestLine: Encodable {
    let role: String
    let text: String
}
private struct AIRequest: Encodable {
    let model: String
    let messages: [AIRequestLine]
}

struct NativeChatGPTWorkspaceView: View {
    @EnvironmentObject private var account: CloudAccountSession

    @State private var status: AIStatus?
    @State private var models: [AIModel] = []
    @State private var model = ""
    @State private var question = ""
    @State private var lines: [AIChatLine] = []
    @State private var busy = false
    @State private var error = ""

    private var isReady: Bool {
        account.feature("ASK_CHATGPT") && status?.sharingEnabled == true &&
        models.contains(where: { $0.slug == model })
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("ASK CHATGPT · NATIVE IOS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Use the Electrical Workbench hosted ChatGPT connection. This screen never asks for, stores or exposes an OpenAI API key.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                    if !account.feature("ASK_CHATGPT") {
                        Label("Ask ChatGPT is not enabled for your verified account, or you are offline.",
                              systemImage: "lock.shield")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                    }
                }
                .aiCard()

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("ACCOUNT CONNECTION")
                            .font(.caption.bold())
                            .foregroundStyle(WorkbenchStyle.cyan)
                        Spacer()
                        Button("Refresh") {
                            Task { await refresh() }
                        }
                        .font(.caption.bold())
                        .disabled(busy || account.isOffline)
                    }
                    if let status {
                        Label(status.sharingEnabled ? "ChatGPT plan sharing connected" :
                                status.connected ? "Signed in · plan use unavailable" :
                                "ChatGPT account not connected",
                              systemImage: status.sharingEnabled ? "checkmark.circle" : "info.circle")
                            .font(.subheadline.weight(.semibold))
                        Text(status.message)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                        if !status.name.isEmpty {
                            Text("Connected: \(status.name)")
                                .font(.caption)
                        }
                    } else {
                        Text(busy ? "Checking account status…" : "Connection status not loaded.")
                            .font(.subheadline)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }

                    Link(destination: URL(string: "https://remote-javafx-portal.onrender.com/chatgpt.html")!) {
                        Label("Connect or manage ChatGPT on portal", systemImage: "arrow.up.right.square")
                            .frame(maxWidth: .infinity, minHeight: 42)
                    }
                    .buttonStyle(.bordered)

                    Text("OAuth connection opens in the browser. Sign in to the same Electrical Workbench username on the portal, complete any authorized ChatGPT consent, then return and tap Refresh. Hosted plan usage may not be enabled on this server.")
                        .font(.caption)
                        .foregroundStyle(WorkbenchStyle.muted)
                }
                .aiCard()

                if isReady {
                    VStack(alignment: .leading, spacing: 9) {
                        Picker("ChatGPT model", selection: $model) {
                            ForEach(models) { m in
                                Text(m.displayName).tag(m.slug)
                            }
                        }
                        .pickerStyle(.menu)
                        TextField("Ask about an electrical calculation or troubleshooting method", text: $question, axis: .vertical)
                            .lineLimit(3...7)
                            .padding(11)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                        Button {
                            Task { await send() }
                        } label: {
                            Label("Ask ChatGPT", systemImage: "paperplane.fill")
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(WorkbenchStyle.cyan)
                        .foregroundStyle(WorkbenchStyle.background)
                        .disabled(busy || question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                    .aiCard()
                }
                if !error.isEmpty {
                    Label(error, systemImage: "exclamationmark.circle")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                        .padding(12)
                }
                if busy { ProgressView("Working…") }

                if !lines.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("CONVERSATION · THIS SESSION")
                                .font(.caption.bold())
                                .foregroundStyle(WorkbenchStyle.cyan)
                            Spacer()
                            Button("Clear") { lines.removeAll() }
                                .font(.caption)
                        }
                        ForEach(lines) { line in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(line.role == "user" ? "YOU" : "CHATGPT")
                                    .font(.caption.bold())
                                    .foregroundStyle(WorkbenchStyle.cyan)
                                Text(line.text)
                                    .font(.subheadline)
                                    .textSelection(.enabled)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    .aiCard()
                }
                Text("AI responses can contain errors. Verify electrical Code references, calculations, safety and manufacturer requirements before acting. Conversation text is sent to the connected ChatGPT service when you tap Ask.")
                    .font(.footnote)
                    .foregroundStyle(WorkbenchStyle.muted)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Ask ChatGPT")
        .navigationBarTitleDisplayMode(.inline)
        .task { await refresh() }
    }

    private func refresh() async {
        guard account.feature("ASK_CHATGPT") && !busy else { return }
        busy = true
        error = ""
        defer { busy = false }
        do {
            let raw = try await account.api("/api/chatgpt/status")
            let current = try JSONDecoder().decode(AIStatus.self, from: raw)
            status = current
            guard current.sharingEnabled else {
                models = []; model = ""
                return
            }
            let data = try await account.api("/api/chatgpt/models")
            let response = try JSONDecoder().decode(AIModelResponse.self, from: data)
            models = response.models
            if !models.contains(where: { $0.slug == model }) {
                model = models.first?.slug ?? ""
            }
        } catch {
            models = []; model = ""
            self.error = error.localizedDescription
        }
    }

    private func send() async {
        guard isReady && !busy else { return }
        let typed = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !typed.isEmpty else { return }
        busy = true
        error = ""
        let pending = AIChatLine(role: "user", text: typed)
        let newLines = lines + [pending]
        question = ""
        defer { busy = false }
        do {
            let request = AIRequest(model: model,
                                    messages: newLines.map { AIRequestLine(role: $0.role, text: $0.text) })
            let data = try await account.api("/api/chatgpt/respond",
                                             method: "POST",
                                             json: JSONEncoder().encode(request))
            let answer = try JSONDecoder().decode(AIResponse.self, from: data)
            lines = newLines + [AIChatLine(role: "assistant", text: answer.text)]
        } catch {
            question = typed
            self.error = error.localizedDescription
        }
    }
}

private struct AICard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func aiCard() -> some View { modifier(AICard()) }
}
