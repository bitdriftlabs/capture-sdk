// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class InternalAutoInstrumentationMessageTests: XCTestCase {
    private var sut: InternalAutoInstrumentationMessage!

    func testMakeLoggingActionLogsInstrumentedEventAtDebugLevel() throws {
        try givenInternalAutoInstrumentationMessage(event: "captureWebVitals")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "[WebView] instrumented captureWebVitals", level: .debug) { fields in
            XCTAssertEqual(fields["_event"], "captureWebVitals")
        }
    }
}

private extension InternalAutoInstrumentationMessageTests {
    func givenInternalAutoInstrumentationMessage(event: String) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "internalAutoInstrumentation",
            "timestamp": 1700000000000,
            "event": "\(event)"
        }
        """
        sut = try decodeWebViewMessage(InternalAutoInstrumentationMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
