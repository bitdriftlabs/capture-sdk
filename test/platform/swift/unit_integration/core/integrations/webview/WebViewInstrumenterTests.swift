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
final class WebViewInstrumenterTests: XCTestCase {
    private var logger: MockLogging!
    private var sut: WebViewInstrumenter!
    private var configuration: WKWebViewConfiguration!

    override func setUp() {
        super.setUp()
        self.logger = MockLogging()
        self.sut = WebViewInstrumenter.make(loggingProvider: WebViewLoggingProvider(logger: self.logger))
        self.configuration = WKWebViewConfiguration()
    }

    func testCaptureInstrumentAddsUserScript() {
        self.whenInstrumenting()
        self.thenUserScriptCount(is: 1)
    }

    func testCaptureInstrumentCalledTwiceOnSameControllerAddsScriptOnce() {
        self.whenInstrumenting()
        self.whenInstrumenting()
        self.thenUserScriptCount(is: 1)
    }

    func testCaptureInstrumentCalledOnConfigurationsSharingControllerAddsScriptOnce() {
        self.whenInstrumenting()

        let otherConfiguration = WKWebViewConfiguration()
        otherConfiguration.userContentController = self.configuration.userContentController
        self.sut.captureInstrument(otherConfiguration)

        self.thenUserScriptCount(is: 1)
    }

    func testCaptureInstrumentDoesNothingWhenRuntimeFlagDisabled() {
        self.sut = WebViewInstrumenter.make(
            loggingProvider: FakeLoggingProvider(logger: self.logger, webviewInstrumentationEnabled: false)
        )

        self.whenInstrumenting()

        self.thenUserScriptCount(is: 0)
    }
}

private struct FakeLoggingProvider: LoggingProvider {
    let logger: Logging?
    let webviewInstrumentationEnabled: Bool

    func getLogging() -> Logging? { self.logger }

    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        if T.self == Bool.self {
            // swiftlint:disable:next force_cast
            return self.webviewInstrumentationEnabled as! T
        }
        return variable.defaultValue
    }
}

private extension WebViewInstrumenterTests {
    func whenInstrumenting() {
        self.sut.captureInstrument(self.configuration)
    }

    func thenUserScriptCount(is count: Int) {
        XCTAssertEqual(self.configuration.userContentController.userScripts.count, count)
    }
}
