// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import WebKit
import XCTest

@MainActor
final class WebViewIntegrationSwizzlingTests: XCTestCase {
    private var logger: MockLogging!

    override func setUp() {
        super.setUp()
        self.logger = MockLogging()
    }

    func testPerIntegrationDisableSwizzlingOverridesEnabledGlobalFlag() {
        self.givenWebViewIntegrationStarted(disableSwizzling: true, globalDisableSwizzling: false)
        let webView = self.whenCreatingWebView()
        self.thenWebViewIsNotInstrumented(webView)
    }
}

private extension WebViewIntegrationSwizzlingTests {
    func givenWebViewIntegrationStarted(disableSwizzling: Bool?, globalDisableSwizzling: Bool) {
        Integration.webView(disableSwizzling: disableSwizzling)
            .start(with: self.logger, disableSwizzling: globalDisableSwizzling)
    }

    func whenCreatingWebView() -> WKWebView {
        WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
    }

    func thenWebViewIsNotInstrumented(_ webView: WKWebView) {
        XCTAssertTrue(webView.configuration.userContentController.userScripts.isEmpty)
    }
}

@MainActor
final class WebViewIntegrationSwizzlingRuntimeFlagTests: XCTestCase {
    private var loggerBridge: MockLoggerBridging!
    private var logger: Logger!

    override func setUp() {
        super.setUp()
        WebViewIntegration.shared.disableWebViewSwizzling()

        self.loggerBridge = MockLoggerBridging()
        self.logger = Logger(
            withAPIKey: "123",
            remoteErrorReporter: nil,
            configuration: .init(
                rootFileURL: FileManager.default.temporaryDirectory.appendingPathComponent(
                    "bitdrift_test_\(UUID().uuidString)"
                )
            ),
            sessionStrategy: .fixed(),
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: false,
            storageProvider: MockStorageProvider(),
            timeProvider: MockTimeProvider(),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: self.loggerBridge)
        )
    }

    override func tearDown() {
        WebViewIntegration.shared.disableWebViewSwizzling()
        super.tearDown()
    }

    func testSwizzlingDisabledByRuntimeFlagSkipsAutomaticInstrumentation() {
        self.loggerBridge.mockRuntimeVariable(.webviewSwizzling, with: false)

        Integration.webView().start(with: self.logger, disableSwizzling: false)
        let webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())

        XCTAssertTrue(webView.configuration.userContentController.userScripts.isEmpty)
    }

    func testSwizzlingDisabledByRuntimeFlagDoesNotAffectManualInstrumentation() {
        self.loggerBridge.mockRuntimeVariable(.webviewSwizzling, with: false)

        Integration.webView().start(with: self.logger, disableSwizzling: false)

        let webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        self.logger.instrument(webView: webView)

        XCTAssertEqual(webView.configuration.userContentController.userScripts.count, 1)
    }
}
