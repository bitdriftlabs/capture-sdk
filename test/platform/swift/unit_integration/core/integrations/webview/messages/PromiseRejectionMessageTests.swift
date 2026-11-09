// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class PromiseRejectionMessageTests: XCTestCase {
    private var sut: PromiseRejectionMessage!

    func testMakeLoggingActionLogsAtErrorLevel() throws {
        try givenPromiseRejectionMessage(reason: "unhandled rejection")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.promiseRejection", level: .error) { fields in
            XCTAssertEqual(fields["_reason"], "unhandled rejection")
        }
    }
}

private extension PromiseRejectionMessageTests {
    func givenPromiseRejectionMessage(reason: String = "reason") throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "promiseRejection",
            "timestamp": 1700000000000,
            "reason": "\(reason)"
        }
        """
        sut = try decodeWebViewMessage(PromiseRejectionMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
