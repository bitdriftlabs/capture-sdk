// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import WebKit

final class WebViewInstrumenter {
    private static let scriptMarker = "/* BitdriftWebViewBridge */"
    private static let messageHandlerName = "BitdriftLogger"

    private let loggingProvider: LoggingProvider
    private let instrumentationConfig: WebViewScriptConfiguration

    static func make(
        loggingProvider: LoggingProvider,
        instrumentationConfig: WebViewScriptConfiguration = .init()
    ) -> WebViewInstrumenter {
        WebViewInstrumenter(loggingProvider: loggingProvider, instrumentationConfig: instrumentationConfig)
    }

    init(loggingProvider: LoggingProvider, instrumentationConfig: WebViewScriptConfiguration) {
        self.loggingProvider = loggingProvider
        self.instrumentationConfig = instrumentationConfig
    }

    func captureInstrument(_ configuration: WKWebViewConfiguration) {
        /// `WKUserContentController`/`WKWebViewConfiguration` are only safe to mutate on the
        /// main thread. If called off the main thread, this method dispatches its work to the
        /// main thread asynchronously instead of blocking the calling thread, to avoid the risk
        /// of deadlocking with a caller that itself may be blocking the main thread.
        if Thread.isMainThread {
            self.performCaptureInstrument(configuration)
        } else {
            DispatchQueue.main.async {
                self.performCaptureInstrument(configuration)
            }
        }
    }

    private func performCaptureInstrument(_ configuration: WKWebViewConfiguration) {
        guard loggingProvider.runtimeValue(.webviewInstrumentation) else {
            return
        }

        let userContentController = configuration.userContentController

        guard !userContentController.userScripts.contains(where: { $0.source.hasPrefix(Self.scriptMarker) }) else {
            return
        }

        let script = WKUserScript(
            source: Self.scriptMarker + WebViewBridgeScript.getScript(configuration: instrumentationConfig),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        userContentController.addUserScript(script)
        userContentController.add(
            ScriptMessageHandler(loggingProvider: loggingProvider),
            name: Self.messageHandlerName
        )
    }
}
