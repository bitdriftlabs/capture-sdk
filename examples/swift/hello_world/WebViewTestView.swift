// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI
import WebKit

/// A view that displays a WKWebView for testing WebView instrumentation
struct WebViewTestView: View {
    @State private var isLoading = true
    @State private var errorMessage: String?
    
    var body: some View {
        VStack {
            if let errorMessage = errorMessage {
                Text("Error: \(errorMessage)")
                    .foregroundColor(.red)
                    .padding()
            } else {
                WebView(isLoading: $isLoading)
                    .overlay(
                        Group {
                            if isLoading {
                                ProgressView("Loading...")
                            }
                        }
                    )
            }
        }
        .navigationTitle("WebView Test")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// UIViewRepresentable wrapper for WKWebView
struct WebView: UIViewRepresentable {
    @Binding var isLoading: Bool
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator
        
        // Instrument the WebView with Capture SDK
        let webViewConfig = WebViewConfiguration(
            capturePageViews: true,
            captureNetworkRequests: true,
            captureNavigationEvents: true,
            captureWebVitals: true,
            captureLongTasks: true,
            captureConsoleLogs: true,
            captureUserInteractions: true,
            captureErrors: true
        )
        
        do {
            guard let logger = Logger.shared else {
                print("❌ Logger not initialized")
                return webView
            }
            try webView.instrument(logger: logger, config: webViewConfig)
            print("✅ WebView instrumented successfully")
        } catch {
            print("❌ Failed to instrument WebView: \(error)")
        }
        
        // Load the test HTML file
        // Try with assets directory first, then fall back to root bundle (Bazel may flatten resources)
        let htmlPath = Bundle.main.path(forResource: "index", ofType: "html", inDirectory: "assets")
            ?? Bundle.main.path(forResource: "index", ofType: "html")
        
        if let htmlPath = htmlPath,
           let htmlString = try? String(contentsOfFile: htmlPath, encoding: .utf8) {
            let baseURL = URL(fileURLWithPath: htmlPath).deletingLastPathComponent()
            webView.loadHTMLString(htmlString, baseURL: baseURL)
        } else {
            print("❌ Failed to load index.html from assets")
            // Debug: List bundle contents
            if let resourcePath = Bundle.main.resourcePath {
                let contents = try? FileManager.default.contentsOfDirectory(atPath: resourcePath)
                print("📁 Bundle contents: \(contents ?? [])")
            }
        }
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
        // No updates needed
    }
    
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: WebView
        
        init(_ parent: WebView) {
            self.parent = parent
        }
        
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
        }
        
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }
        
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            print("❌ WebView navigation failed: \(error)")
        }
        
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            print("❌ WebView provisional navigation failed: \(error)")
        }
    }
}

struct WebViewTestView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            WebViewTestView()
        }
    }
}
