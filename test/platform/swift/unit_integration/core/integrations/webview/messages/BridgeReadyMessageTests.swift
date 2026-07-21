// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class BridgeReadyMessageTests: XCTestCase {
    private var sut: BridgeReadyMessage!

    func testMakeLoggingActionWithConfigIncludesConfigField() throws {
        try givenBridgeReadyMessage(instrumentationConfig: .init(captureWebVitals: false))
        let action = whenMakingLoggingAction()
        thenActionLogsInitialized(action) { fields in
            XCTAssertEqual(fields["_url"], "https://example.com")

            guard let configJSON = fields["_config"],
                  let configData = configJSON.data(using: .utf8),
                  let decodedConfig = try? JSONDecoder().decode(WebViewScriptConfiguration.self, from: configData)
            else {
                XCTFail("missing or invalid _config field")
                return
            }
            XCTAssertEqual(decodedConfig, WebViewScriptConfiguration(captureWebVitals: false))
        }
    }

    func testMakeLoggingActionWithoutConfigOmitsConfigField() throws {
        try givenBridgeReadyMessage(instrumentationConfig: nil)
        let action = whenMakingLoggingAction()
        thenActionLogsInitialized(action) { fields in
            XCTAssertNil(fields["_config"])
        }
    }

    func testMakeLoggingActionDoesNotIncludeTimestampField() throws {
        try givenBridgeReadyMessage(instrumentationConfig: nil)
        let action = whenMakingLoggingAction()
        thenActionLogsInitialized(action) { fields in
            XCTAssertNil(fields["_timestamp"])
        }
    }
}

private extension BridgeReadyMessageTests {
    func givenBridgeReadyMessage(instrumentationConfig: WebViewScriptConfiguration?) throws {
        let configJSON = instrumentationConfig.map { "\($0.toJSONString())" } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "bridgeReady",
            "timestamp": 1700000000000,
            "url": "https://example.com",
            "instrumentationConfig": \(configJSON)
        }
        """
        sut = try decodeWebViewMessage(BridgeReadyMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }

    func thenActionLogsInitialized(
        _ action: WebViewLoggingAction?,
        fields assertFields: ([String: String]) -> Void
    ) {
        assertWebLogAction(action, message: "webview.initialized", level: .debug, fields: assertFields)
    }
}
