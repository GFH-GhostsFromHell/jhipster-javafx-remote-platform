import SwiftUI
import WebKit
import UIKit

/// The same HTTPS site and WebKit cookie store are used by every cloud tool screen.
enum PortalEndpoint {
    static let root = URL(string: "https://remote-javafx-portal.onrender.com")!

    static func url(for path: String) -> URL {
        // Never use a user-supplied remote URL for authenticated tools.
        let parts = path.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)
        let relativePath = String(parts.first ?? "")
        let cleanPath = relativePath.hasPrefix("/") ? String(relativePath.dropFirst()) : relativePath
        var components = URLComponents(url: root.appendingPathComponent(cleanPath), resolvingAgainstBaseURL: false)!
        if parts.count == 2 { components.fragment = String(parts[1]) }
        return components.url ?? root
    }

    static func isTrusted(_ url: URL) -> Bool {
        url.scheme == "https" && url.host == root.host
    }
}

@MainActor
final class PortalBrowserModel: ObservableObject {
    @Published var loading = true
    @Published var canGoBack = false
    @Published var canGoForward = false
    @Published var errorMessage: String?
    weak var view: WKWebView?

    func back() {
        guard view?.canGoBack == true else { return }
        view?.goBack()
    }

    func forward() {
        guard view?.canGoForward == true else { return }
        view?.goForward()
    }

    func reload() {
        errorMessage = nil
        view?.reload()
    }

    func update(from webView: WKWebView) {
        loading = webView.isLoading
        canGoBack = webView.canGoBack
        canGoForward = webView.canGoForward
    }
}

struct PortalScreen: View {
    let title: String
    let path: String

    @StateObject private var browser = PortalBrowserModel()

    var body: some View {
        VStack(spacing: 0) {
            if browser.loading {
                ProgressView()
                    .progressViewStyle(.linear)
                    .tint(WorkbenchStyle.cyan)
                    .accessibilityLabel("Loading cloud tool")
            }

            if let error = browser.errorMessage {
                HStack(spacing: 10) {
                    Image(systemName: "wifi.exclamationmark")
                        .foregroundStyle(.orange)
                    Text(error)
                        .font(.caption)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 4)
                    Button("Retry") { browser.reload() }
                        .font(.caption.weight(.bold))
                }
                .padding(10)
                .background(WorkbenchStyle.surface)
            }

            PortalWebView(path: path, model: browser)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack(spacing: 18) {
                Button { browser.back() } label: {
                    Label("Back", systemImage: "chevron.left")
                }
                .disabled(!browser.canGoBack)

                Button { browser.forward() } label: {
                    Label("Forward", systemImage: "chevron.right")
                }
                .disabled(!browser.canGoForward)

                Spacer(minLength: 8)

                Button { browser.reload() } label: {
                    Image(systemName: "arrow.clockwise")
                        .accessibilityLabel("Reload tool")
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(WorkbenchStyle.background)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct PortalWebView: UIViewRepresentable {
    let path: String
    @ObservedObject var model: PortalBrowserModel

    func makeCoordinator() -> Coordinator {
        Coordinator(model: model)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.allowsInlineMediaPlayback = true

        // Keep forms readable without overriding the field tools' dark/light themes.
        let mobileCSS = """
        (function () {
          var style = document.createElement('style');
          style.textContent = '@media(max-width:620px) {' +
              'html{-webkit-text-size-adjust:100%;}' +
              'body{overflow-x:hidden;}' +
              'button,input,select,textarea{max-width:100%;}' +
              'button{min-height:44px;}' +
              'input,select,textarea{font-size:16px!important;}' +
              '}';
          document.head.appendChild(style);
        })();
        """
        config.userContentController.addUserScript(
            WKUserScript(source: mobileCSS, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        )

        let browser = WKWebView(frame: .zero, configuration: config)
        browser.navigationDelegate = context.coordinator
        browser.uiDelegate = context.coordinator
        browser.allowsBackForwardNavigationGestures = true
        browser.scrollView.keyboardDismissMode = .interactive
        browser.isOpaque = false
        browser.backgroundColor = UIColor.systemBackground

        model.view = browser
        browser.load(URLRequest(url: PortalEndpoint.url(for: path),
                                cachePolicy: .useProtocolCachePolicy,
                                timeoutInterval: 30))
        return browser
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // Navigation is managed by WKWebView; SwiftUI redraws must not reset web forms.
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        private let model: PortalBrowserModel

        init(model: PortalBrowserModel) {
            self.model = model
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            Task { @MainActor in
                model.errorMessage = nil
                model.update(from: webView)
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            Task { @MainActor in model.update(from: webView) }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            handleFailure(error, webView: webView)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            handleFailure(error, webView: webView)
        }

        private func handleFailure(_ error: Error, webView: WKWebView) {
            guard (error as NSError).code != NSURLErrorCancelled else { return }
            Task { @MainActor in
                model.errorMessage = "Unable to load this tool. Check your connection."
                model.update(from: webView)
            }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }

            if PortalEndpoint.isTrusted(url) || url.scheme == "about" {
                decisionHandler(.allow)
            } else {
                // Third-party sites, payment providers, and OAuth go to the system browser.
                if ["https", "http", "mailto", "tel"].contains(url.scheme ?? "") {
                    UIApplication.shared.open(url)
                }
                decisionHandler(.cancel)
            }
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                     for navigationAction: WKNavigationAction,
                     windowFeatures: WKWindowFeatures) -> WKWebView? {
            if navigationAction.targetFrame == nil,
               let url = navigationAction.request.url {
                if PortalEndpoint.isTrusted(url) {
                    webView.load(URLRequest(url: url))
                } else if url.scheme == "https" {
                    UIApplication.shared.open(url)
                }
            }
            return nil
        }
    }
}
