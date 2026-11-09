// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class ErrorMessageTests: XCTestCase {
    private var sut: ErrorMessage!

    func testMakeLoggingActionLogsAtErrorLevel() throws {
        try givenErrorMessage()
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.error", level: .error)
    }

    func testMakeLoggingActionIncludesErrorFields() throws {
        try givenErrorMessage(
            name: "TypeError",
            message: "Cannot read property 'x' of undefined"
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.error", level: .error) { fields in
            XCTAssertEqual(fields["_name"], "TypeError")
            XCTAssertEqual(fields["_message"], "Cannot read property 'x' of undefined")
        }
    }
}

private extension ErrorMessageTests {
    func givenErrorMessage(
        name: String = "Error",
        message: String = "boom"
    ) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "error",
            "timestamp": 1700000000000,
            "name": "\(name)",
            "message": "\(message)"
        }
        """
        sut = try decodeWebViewMessage(ErrorMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
