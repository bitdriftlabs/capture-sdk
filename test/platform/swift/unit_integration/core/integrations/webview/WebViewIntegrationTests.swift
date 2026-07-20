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

/// A fake `WKScriptMessage` that lets tests inject an arbitrary `body` without going through a real
/// `WKUserContentController`/JavaScript round-trip. `WKScriptMessage.body` is `open`, so it can be
/// overridden directly.
private final class MockWKScriptMessage: WKScriptMessage {
    private let mockBody: Any

    init(body: Any) {
        self.mockBody = body
        super.init()
    }

    override var body: Any { self.mockBody }
}

@MainActor
final class WebViewIntegrationTests: XCTestCase {
    private var logger: MockLogging!
    private var sut: ScriptMessageHandler!
    private var userContentController: WKUserContentController!

    override func setUp() {
        super.setUp()
        self.logger = MockLogging()
        Integration.webView().start(with: self.logger, disableSwizzling: true)
        self.sut = ScriptMessageHandler(loggingProvider: WebViewLoggingProvider())
        self.userContentController = WKUserContentController()
    }

    // MARK: - WebViewLoggingProvider / WebViewIntegration wiring

    func testWebViewLoggingProviderReturnsLoggerConfiguredThroughIntegration() throws {
        let logging = try XCTUnwrap(WebViewLoggingProvider().getLogging() as? MockLogging)
        XCTAssertTrue(logging === self.logger)
    }

    // MARK: - ScriptMessageHandler dispatch

    func testDidReceiveCustomLogMessageLogsItsMessageAndFields() throws {
        self.logger.logExpectation = self.expectation(description: "log emitted")

        self.whenReceivingMessage(body: """
        {"tag":"t","v":1,"type":"customLog","timestamp":1,"level":"info","message":"hello from web"}
        """)

        self.wait(for: [self.logger.logExpectation!], timeout: 1)
        XCTAssertEqual(self.logger.logs.first?.message(), "hello from web")
    }

    func testDidReceiveNetworkRequestMessageLogsRequestAndResponse() throws {
        self.logger.logRequestExpectation = self.expectation(description: "request logged")
        self.logger.logResponseExpectation = self.expectation(description: "response logged")

        self.whenReceivingMessage(body: """
        {"tag":"t","v":1,"type":"networkRequest","timestamp":1,"requestId":"r1","method":"GET",\
        "url":"https://example.com/ping","statusCode":200,"durationMs":10,"success":true,"error":null,\
        "requestType":"fetch","timing":null}
        """)

        self.wait(
            for: [self.logger.logRequestExpectation!, self.logger.logResponseExpectation!],
            timeout: 1
        )
        XCTAssertEqual(self.logger.logs.count, 2)
    }

    func testDidReceiveMessageWithNonStringBodyDoesNotLogAnything() {
        let noLogExpectation = self.expectation(description: "no log emitted")
        noLogExpectation.isInverted = true
        self.logger.logExpectation = noLogExpectation

        self.whenReceivingMessage(body: NSNumber(value: 1))

        self.wait(for: [noLogExpectation], timeout: 0.3)
        XCTAssertTrue(self.logger.logs.isEmpty)
    }

    func testDidReceiveMessageWithUnknownTypeDoesNotLogAnything() {
        let noLogExpectation = self.expectation(description: "no log emitted")
        noLogExpectation.isInverted = true
        self.logger.logExpectation = noLogExpectation

        self.whenReceivingMessage(body: """
        {"tag":"t","v":1,"type":"unknownType","timestamp":1}
        """)

        self.wait(for: [noLogExpectation], timeout: 0.3)
        XCTAssertTrue(self.logger.logs.isEmpty)
    }

    func testDidReceiveMalformedJSONBodyDoesNotLogAnything() {
        let noLogExpectation = self.expectation(description: "no log emitted")
        noLogExpectation.isInverted = true
        self.logger.logExpectation = noLogExpectation

        self.whenReceivingMessage(body: "not json")

        self.wait(for: [noLogExpectation], timeout: 0.3)
        XCTAssertTrue(self.logger.logs.isEmpty)
    }
}

private extension WebViewIntegrationTests {
    func whenReceivingMessage(body: Any) {
        self.sut.userContentController(
            self.userContentController,
            didReceive: MockWKScriptMessage(body: body)
        )
    }
}
