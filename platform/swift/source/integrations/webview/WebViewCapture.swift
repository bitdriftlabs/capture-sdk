// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
@preconcurrency import WebKit

/// WebView instrumentation for capturing page load events, performance metrics,
/// and network activity from within WebViews.
///
/// This class sets up a JavaScript bridge to capture Core Web Vitals and network
/// requests made from within the web content.
///
/// Usage:
/// ```swift
/// let webView = WKWebView()
/// WebViewCapture.instrument(webView)
/// webView.load(URLRequest(url: URL(string: "https://example.com")!))
/// ```
public final class WebViewCapture: NSObject {
    private static let bridgeName = "BitdriftLogger"

    /// Tracks instrumented WebViews to prevent double-instrumentation
    private static var instrumentedWebViews = NSHashTable<WKWebView>.weakObjects()
    private static let lock = NSLock()

    /// Message handler that receives messages from the JavaScript bridge
    private let messageHandler: WebViewMessageHandler

    /// Navigation delegate that wraps the original delegate
    private var navigationDelegate: WebViewNavigationDelegate?

    private init(logger: Logging?) {
        self.messageHandler = WebViewMessageHandler(logger: logger)
        super.init()
    }

    /// Instruments a WKWebView to capture page load events, Core Web Vitals,
    /// and network requests.
    ///
    /// This method:
    /// - Injects the Bitdrift JavaScript bridge at document start
    /// - Registers a message handler for receiving bridge messages
    /// - Wraps the navigation delegate to capture page load events
    ///
    /// - parameter webView: The WKWebView to instrument.
    /// - parameter logger:  Optional logger instance. If nil, uses `Capture.Logger.shared`.
    @MainActor
    public static func instrument(_ webView: WKWebView, logger: Logging? = nil) {
        lock.lock()
        defer { lock.unlock() }

        // Avoid double-instrumentation
        if instrumentedWebViews.contains(webView) {
            return
        }

        let effectiveLogger = logger ?? Capture.Logger.shared
        let capture = WebViewCapture(logger: effectiveLogger)

        // Add script message handler
        webView.configuration.userContentController.add(
            capture.messageHandler,
            name: bridgeName
        )

        // Inject bridge script at document start
        let script = WKUserScript(
            source: WebViewBridgeScript.script,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        webView.configuration.userContentController.addUserScript(script)

        // Wrap navigation delegate for page load tracking
        let navigationDelegate = WebViewNavigationDelegate(
            original: webView.navigationDelegate,
            logger: effectiveLogger,
            messageHandler: capture.messageHandler
        )
        capture.navigationDelegate = navigationDelegate
        webView.navigationDelegate = navigationDelegate

        instrumentedWebViews.add(webView)

        effectiveLogger?.log(
            level: .debug,
            message: "WebView instrumented",
            fields: nil
        )
    }

    /// Removes instrumentation from a WKWebView.
    ///
    /// - parameter webView: The WKWebView to remove instrumentation from.
    @MainActor
    public static func removeInstrumentation(from webView: WKWebView) {
        lock.lock()
        defer { lock.unlock() }

        webView.configuration.userContentController.removeScriptMessageHandler(forName: bridgeName)
        webView.configuration.userContentController.removeAllUserScripts()

        instrumentedWebViews.remove(webView)
    }
}

// MARK: - Navigation Delegate

/// Wraps an existing WKNavigationDelegate to capture page load events
private final class WebViewNavigationDelegate: NSObject, WKNavigationDelegate {
    private weak var original: WKNavigationDelegate?
    private weak var logger: Logging?
    private let messageHandler: WebViewMessageHandler

    private var pageLoadStartTime: CFAbsoluteTime?
    private var currentURL: URL?

    init(original: WKNavigationDelegate?, logger: Logging?, messageHandler: WebViewMessageHandler) {
        self.original = original
        self.logger = logger
        self.messageHandler = messageHandler
        super.init()
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        pageLoadStartTime = CFAbsoluteTimeGetCurrent()
        currentURL = webView.url
        messageHandler.bridgeReady = false

        original?.webView?(webView, didStartProvisionalNavigation: navigation)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let duration: TimeInterval
        if let startTime = pageLoadStartTime {
            duration = CFAbsoluteTimeGetCurrent() - startTime
        } else {
            duration = 0
        }

        let fields: Fields = [
            "_url": webView.url?.absoluteString ?? "",
            "_durationMs": String(Int(duration * 1000)),
            "_bridgeReady": String(messageHandler.bridgeReady),
        ]

        if !messageHandler.bridgeReady {
            logger?.log(
                level: .warning,
                message: "WebView bridge not ready before page finished",
                fields: fields
            )
        }

        logger?.log(
            level: .debug,
            message: "webview.pageLoad",
            fields: fields
        )

        pageLoadStartTime = nil
        original?.webView?(webView, didFinish: navigation)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let duration: TimeInterval
        if let startTime = pageLoadStartTime {
            duration = CFAbsoluteTimeGetCurrent() - startTime
        } else {
            duration = 0
        }

        let fields: Fields = [
            "_url": webView.url?.absoluteString ?? "",
            "_durationMs": String(Int(duration * 1000)),
            "_error": error.localizedDescription,
        ]

        logger?.log(
            level: .warning,
            message: "webview.pageLoad.failed",
            fields: fields,
            error: error
        )

        pageLoadStartTime = nil
        original?.webView?(webView, didFail: navigation, withError: error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let fields: Fields = [
            "_url": currentURL?.absoluteString ?? "",
            "_error": error.localizedDescription,
        ]

        logger?.log(
            level: .warning,
            message: "webview.pageLoad.provisionalFailed",
            fields: fields,
            error: error
        )

        pageLoadStartTime = nil
        original?.webView?(webView, didFailProvisionalNavigation: navigation, withError: error)
    }

    // Forward all other delegate methods to the original

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let original,
           original.responds(to: #selector(WKNavigationDelegate.webView(_:decidePolicyFor:decisionHandler:) as (WKNavigationDelegate) -> ((WKWebView, WKNavigationAction, @escaping (WKNavigationActionPolicy) -> Void) -> Void)?)) {
            original.webView?(webView, decidePolicyFor: navigationAction, decisionHandler: decisionHandler)
        } else {
            decisionHandler(.allow)
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if let original,
           original.responds(to: #selector(WKNavigationDelegate.webView(_:decidePolicyFor:decisionHandler:) as (WKNavigationDelegate) -> ((WKWebView, WKNavigationResponse, @escaping (WKNavigationResponsePolicy) -> Void) -> Void)?)) {
            original.webView?(webView, decidePolicyFor: navigationResponse, decisionHandler: decisionHandler)
        } else {
            decisionHandler(.allow)
        }
    }
}
