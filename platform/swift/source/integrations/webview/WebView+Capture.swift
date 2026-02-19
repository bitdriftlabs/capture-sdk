// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import WebKit

/// Error types for WebView instrumentation
public enum WebViewInstrumentationError: Error {
    /// The WebView has already been instrumented
    case alreadyInstrumented
}

extension WKWebView {
    /// Key used to track whether this WebView has been instrumented
    private static var instrumentedKey: UInt8 = 0

    /// Key used to store the message handler
    private static var messageHandlerKey: UInt8 = 0

    /// Indicates whether this WebView has been instrumented with Capture SDK
    private var isInstrumented: Bool {
        get {
            objc_getAssociatedObject(self, &Self.instrumentedKey) as? Bool ?? false
        }
        set {
            objc_setAssociatedObject(
                self,
                &Self.instrumentedKey,
                newValue,
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }

    /// Stores the message handler to keep it alive
    private var messageHandler: WebViewBridgeMessageHandler? {
        get {
            objc_getAssociatedObject(self, &Self.messageHandlerKey) as? WebViewBridgeMessageHandler
        }
        set {
            objc_setAssociatedObject(
                self,
                &Self.messageHandlerKey,
                newValue,
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }

    /// Instruments this WKWebView to capture performance metrics, network requests,
    /// and user interactions through the Capture SDK.
    ///
    /// This method must be called before loading any content in the WebView. It injects
    /// the necessary JavaScript bridge and sets up message handlers to forward WebView
    /// events to the Capture SDK logger.
    ///
    /// The method is idempotent - calling it multiple times on the same WebView instance
    /// will have no effect after the first successful call.
    ///
    /// Example usage:
    /// ```swift
    /// let webView = WKWebView()
    /// try webView.instrument(logger: Capture.Logger.shared, config: webViewConfig)
    /// webView.load(URLRequest(url: url))
    /// ```
    ///
    /// - parameter logger: The Capture SDK logger instance to use for logging WebView events
    /// - parameter config: The WebView configuration specifying what to capture
    ///
    /// - throws: `WebViewInstrumentationError.alreadyInstrumented` if this WebView has already
    ///           been instrumented
    public func instrument(
        logger: Logging,
        config: WebViewConfiguration
    ) throws {
        // Check if already instrumented to ensure idempotency
        guard !self.isInstrumented else {
            throw WebViewInstrumentationError.alreadyInstrumented
        }

        // Create and store the message handler
        let handler = WebViewBridgeMessageHandler(logger: logger)
        self.messageHandler = handler

        // Add the script message handler to receive messages from JavaScript
        self.configuration.userContentController.add(handler, name: "BitdriftLogger")

        // Generate the JavaScript bridge script with the provided configuration
        let script = WebViewBridgeScript.getScript(config: config)

        // Create a user script that runs at document start
        let userScript = WKUserScript(
            source: script,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )

        // Add the script to the user content controller
        self.configuration.userContentController.addUserScript(userScript)

        // Mark as instrumented
        self.isInstrumented = true

        logger.log(
            level: .debug,
            message: "WebView instrumented successfully",
            fields: [
                "_source": "webview",
                "_capture_page_views": String(config.capturePageViews),
                "_capture_network": String(config.captureNetworkRequests),
                "_capture_web_vitals": String(config.captureWebVitals),
            ]
        )
    }
}
