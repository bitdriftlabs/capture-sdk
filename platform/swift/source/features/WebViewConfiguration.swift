// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Configuration for WebView instrumentation.
///
/// When provided to ``Configuration``, enables automatic WebView monitoring including
/// Core Web Vitals, page load events, and network activity capture.
///
/// If `nil` is passed to ``Configuration/webViewConfiguration``, WebView monitoring
/// is disabled.
///
/// ```swift
/// let webViewConfig = WebViewConfiguration(
///     capturePageViews: true,
///     captureNetworkRequests: true,
///     captureWebVitals: true
/// )
/// ```
public struct WebViewConfiguration {
    /// Whether to capture page view tracking events. Defaults to false.
    public var capturePageViews: Bool
    
    /// Whether to capture network requests made from the WebView. Defaults to false.
    public var captureNetworkRequests: Bool
    
    /// Whether to capture navigation events. Defaults to false.
    public var captureNavigationEvents: Bool
    
    /// Whether to capture Core Web Vitals (LCP, FCP, CLS, INP, TTFB). Defaults to false.
    public var captureWebVitals: Bool
    
    /// Whether to capture long tasks that block the main thread. Defaults to false.
    public var captureLongTasks: Bool
    
    /// Whether to capture JavaScript console.log/warn/error messages. Defaults to false.
    public var captureConsoleLogs: Bool
    
    /// Whether to capture user interactions (clicks, rage clicks, etc.). Defaults to false.
    public var captureUserInteractions: Bool
    
    /// Whether to capture JavaScript errors, promise rejections, and resource errors. Defaults to false.
    public var captureErrors: Bool
    
    /// Initializes a new instance of the WebView configuration.
    ///
    /// - parameter capturePageViews:         Whether to capture page view tracking events. Defaults to false.
    /// - parameter captureNetworkRequests:   Whether to capture network requests made from the WebView. Defaults to false.
    /// - parameter captureNavigationEvents:  Whether to capture navigation events. Defaults to false.
    /// - parameter captureWebVitals:         Whether to capture Core Web Vitals (LCP, FCP, CLS, INP, TTFB). Defaults to false.
    /// - parameter captureLongTasks:         Whether to capture long tasks that block the main thread. Defaults to false.
    /// - parameter captureConsoleLogs:       Whether to capture JavaScript console.log/warn/error messages. Defaults to false.
    /// - parameter captureUserInteractions:  Whether to capture user interactions (clicks, rage clicks, etc.). Defaults to false.
    /// - parameter captureErrors:            Whether to capture JavaScript errors, promise rejections, and resource errors. Defaults to false.
    public init(
        capturePageViews: Bool = false,
        captureNetworkRequests: Bool = false,
        captureNavigationEvents: Bool = false,
        captureWebVitals: Bool = false,
        captureLongTasks: Bool = false,
        captureConsoleLogs: Bool = false,
        captureUserInteractions: Bool = false,
        captureErrors: Bool = false
    ) {
        self.capturePageViews = capturePageViews
        self.captureNetworkRequests = captureNetworkRequests
        self.captureNavigationEvents = captureNavigationEvents
        self.captureWebVitals = captureWebVitals
        self.captureLongTasks = captureLongTasks
        self.captureConsoleLogs = captureConsoleLogs
        self.captureUserInteractions = captureUserInteractions
        self.captureErrors = captureErrors
    }
}

extension WebViewConfiguration {
    /// Converts the configuration to a JSON string for injection into the WebView JavaScript.
    func toJSON() -> String {
        let dict: [String: Any] = [
            "capturePageViews": capturePageViews,
            "captureNetworkRequests": captureNetworkRequests,
            "captureNavigationEvents": captureNavigationEvents,
            "captureWebVitals": captureWebVitals,
            "captureLongTasks": captureLongTasks,
            "captureConsoleLogs": captureConsoleLogs,
            "captureUserInteractions": captureUserInteractions,
            "captureErrors": captureErrors
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: dict, options: []),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return "{}"
        }
        
        return jsonString
    }
}
