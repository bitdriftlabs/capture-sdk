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
final class LoggingWebViewTests: XCTestCase {
    private var logger: MockLogging!
    private var webView: WKWebView!

    override func setUp() {
        super.setUp()
        self.logger = MockLogging()
        self.webView = WKWebView()
    }

    func testInstrumentAddsUserScriptToWebViewConfiguration() {
        self.whenInstrumenting()
        self.thenUserScriptCount(is: 1)
    }

    func testInstrumentCalledTwiceAddsUserScriptOnce() {
        self.whenInstrumenting()
        self.whenInstrumenting()
        self.thenUserScriptCount(is: 1)
    }
}

private extension LoggingWebViewTests {
    func whenInstrumenting() {
        self.logger.instrument(webView: self.webView)
    }

    func thenUserScriptCount(is count: Int) {
        XCTAssertEqual(self.webView.configuration.userContentController.userScripts.count, count)
    }
}
