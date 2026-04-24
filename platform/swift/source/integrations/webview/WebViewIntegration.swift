// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import WebKit

extension Integration {
    public static func webView() -> Integration {
        .init { logger, disableSwizzling, _ in
            WebViewIntegration.shared.start(
                logger: logger,
                disableSwizzling: disableSwizzling
            )
        }
    }
}

struct WebViewLoggingProvider: LoggingProvider {
    func getLogging() -> (any Logging)? {
        WebViewIntegration.shared.getLogger()
    }
}

final class WebViewIntegration {
    private var hasBeenSwizzled = Atomic(false)
    private let underlyingLogger = Atomic<Logging?>(nil)

    fileprivate static let shared: WebViewIntegration = .init()

    func start(
        logger: Logging,
        disableSwizzling: Bool
    ) {
        guard !self.hasBeenSwizzled.load() else {
            return
        }

        self.hasBeenSwizzled.update { swizzled in
            swizzled.toggle()

            if disableSwizzling {
                return
            }

            exchangeInstanceMethod(
                class: WKWebView.getClass(),
                selector: #selector(WKWebView.init(frame:configuration:)),
                with: #selector(WKWebView.cap_init(frame:configuration:))
            )
        }
    }

    func getLogger() -> Logging? {
        underlyingLogger.load()
    }
}

extension WKWebView {
    @objc
    func cap_init(frame: CGRect, configuration: WKWebViewConfiguration) -> WKWebView {
        let script = WKUserScript(
            source: WebViewBridgeScript.getScript(configuration: .init()),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )

        let userContentController = configuration.userContentController
        userContentController.addUserScript(script)
        userContentController.add(ScriptMessageHandler(loggingProvider: WebViewLoggingProvider()), name: "BitdriftLogger")
        let webView = self.cap_init(frame: frame, configuration: configuration)
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        return webView
    }

    static func getClass() -> AnyClass {
        return WKWebView.self
    }
}

class ScriptMessageHandler: NSObject, WKScriptMessageHandler {
    private var loggingProvider: WebViewLoggingProvider?

    init(loggingProvider: WebViewLoggingProvider) {
        self.loggingProvider = loggingProvider
    }

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        print("Received message with name: \(message.name), body: \(message.body)")
    }
}
