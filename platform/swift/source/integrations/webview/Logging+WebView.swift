// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#if canImport(WebKit)
import WebKit

public extension Logging {
    /// Manually instruments the given web view so that events captured by the bundled JavaScript SDK are
    /// emitted as Capture SDK logs by the receiver. Use this when the `webView()` integration was enabled
    /// with `disableSwizzling: true`.
    ///
    /// Calling this method more than once for web views that share the same `WKUserContentController` has
    /// no additional effect.
    ///
    /// - Warning: This API is experimental
    ///
    /// - parameter webView: The web view to instrument.
    func instrument(webView: WKWebView) {
        WebViewInstrumenter
            .make(loggingProvider: WebViewLoggingProvider(logger: self))
            .captureInstrument(webView.configuration)
    }
}
#endif
