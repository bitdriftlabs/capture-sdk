// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import WebKit

extension Integration {
    /// - parameter disableSwizzling: Overrides the global swizzling setting, to disable swizzling in
    ///                               favor of manual instrumentation without affecting other
    ///                               integrations. Defaults to `nil`, which falls back to the global
    ///                               setting.
    ///
    /// - returns: The webview integration.
    public static func webView(disableSwizzling: Bool? = nil) -> Integration {
        .init { logger, globalDisableSwizzling, _ in
            WebViewIntegration.shared.start(
                logger: logger,
                disableSwizzling: disableSwizzling ?? globalDisableSwizzling
            )
        }
    }
}

final class WebViewIntegration {
    private var hasSwizzledWebViewInit = Atomic(false)
    private let underlyingLogger = Atomic<Logging?>(nil)

    static let shared: WebViewIntegration = .init()

    func start(
        logger: Logging,
        disableSwizzling: Bool
    ) {
        underlyingLogger.update { storedLogger in
            storedLogger = logger
        }

        hasSwizzledWebViewInit.update { hasSwizzled in
            guard !hasSwizzled, !disableSwizzling, logger.runtimeValue(.webviewSwizzling) else {
                return
            }

            hasSwizzled = true
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

extension WebViewIntegration {
    /// Exchanging the method twice should in theory restore the original implementation.
    /// Note: This should only be used in tests.
    func disableWebViewSwizzling() {
        hasSwizzledWebViewInit.update { hasSwizzled in
            guard hasSwizzled else {
                return
            }

            hasSwizzled = false
            exchangeInstanceMethod(
                class: WKWebView.getClass(),
                selector: #selector(WKWebView.init(frame:configuration:)),
                with: #selector(WKWebView.cap_init(frame:configuration:))
            )
        }
    }
}

extension WKWebView {
    @objc
    func cap_init(frame: CGRect, configuration: WKWebViewConfiguration) -> WKWebView {
        WebViewInstrumenter
            .make(loggingProvider: WebViewLoggingProvider())
            .captureInstrument(configuration)

        let webView = cap_init(frame: frame, configuration: configuration)
        #if DEBUG
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        #endif
        return webView
    }

    static func getClass() -> AnyClass {
        return WKWebView.self
    }
}

class ScriptMessageHandler: NSObject, WKScriptMessageHandler {
    private let processingQueue: DispatchQueue
    private var loggingProvider: LoggingProvider?
    private var currentPageViewSpanID: String?
    private var activePageViewSpans = [String: Span]()

    init(
        loggingProvider: LoggingProvider,
        processingQueue: DispatchQueue = DispatchQueue(label: "io.bitdrift.capture.webview.processing")
    ) {
        self.loggingProvider = loggingProvider
        self.processingQueue = processingQueue
    }

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        let body = message.body
        processingQueue.async {
            do {
                guard let decodedMessage = try WebViewMessageParser.decode(from: body) as? any WebViewLoggableMessage else {
                    return
                }

                let context = WebViewLoggingContext(
                    currentPageViewSpanID: self.currentPageViewSpanID,
                    activePageViewSpans: self.activePageViewSpans
                )

                if let action = decodedMessage.makeLoggingAction(context: context) {
                    self.execute(action: action)
                }
            } catch let exception {
                print(exception.localizedDescription)
            }
        }
    }

    private func execute(action: WebViewLoggingAction) {
        guard let logger = loggingProvider?.getLogging() else {
            return
        }

        switch action {
        case .log(let level, let message, let fields):
            logger.log(level: level, message: message, fields: fields)
        case .network(let request, let response):
            logger.log(request, file: nil, line: nil, function: nil)
            logger.log(response, file: nil, line: nil, function: nil)
        case .startSpan(let id, let name, let level, let fields, let startTimeInterval, let parentSpanID):
            let span = logger.startSpan(
                name: name,
                level: level,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                startTimeInterval: startTimeInterval,
                parentSpanID: parentSpanID
            )
            activePageViewSpans[id] = span
            currentPageViewSpanID = id
        case .endSpan(let id, let result, let fields, let endTimeInterval):
            activePageViewSpans.removeValue(forKey: id)?.end(
                result,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                endTimeInterval: endTimeInterval
            )

            if currentPageViewSpanID == id {
                currentPageViewSpanID = nil
            }
        case .completeSpan(
            let name,
            let level,
            let fields,
            let startTimeInterval,
            let endTimeInterval,
            let parentSpanID,
            let result
        ):
            logger.startSpan(
                name: name,
                level: level,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                startTimeInterval: startTimeInterval,
                parentSpanID: parentSpanID
            ).end(
                result,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                endTimeInterval: endTimeInterval
            )
        }
    }
}
