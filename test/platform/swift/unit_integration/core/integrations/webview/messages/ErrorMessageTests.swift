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

    func testMakeLoggingActionIncludesAllFields() throws {
        try givenErrorMessage(
            name: "TypeError",
            message: "Cannot read property 'x' of undefined",
            stack: "at foo (app.js:1:1)",
            filename: "app.js",
            lineno: 10,
            colno: 5
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.error", level: .error) { fields in
            XCTAssertEqual(fields["_name"], "TypeError")
            XCTAssertEqual(fields["_message"], "Cannot read property 'x' of undefined")
            XCTAssertEqual(fields["_stack"], "at foo (app.js:1:1)")
            XCTAssertEqual(fields["_filename"], "app.js")
            XCTAssertEqual(fields["_lineno"], "10")
            XCTAssertEqual(fields["_colno"], "5")
        }
    }

    func testMakeLoggingActionOmitsMissingOptionalFields() throws {
        try givenErrorMessage(stack: nil, filename: nil, lineno: nil, colno: nil)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.error", level: .error) { fields in
            XCTAssertNil(fields["_stack"])
            XCTAssertNil(fields["_filename"])
            XCTAssertNil(fields["_lineno"])
            XCTAssertNil(fields["_colno"])
        }
    }
}

private extension ErrorMessageTests {
    func givenErrorMessage(
        name: String = "Error",
        message: String = "boom",
        stack: String? = "stack trace",
        filename: String? = "app.js",
        lineno: Int? = 1,
        colno: Int? = 1
    ) throws {
        let linenoJSON: String = lineno.map { String($0) } ?? "null"
        let colnoJSON: String = colno.map { String($0) } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "error",
            "timestamp": 1700000000000,
            "name": "\(name)",
            "message": "\(message)",
            "stack": \(stack.map { "\"\($0)\"" } ?? "null"),
            "filename": \(filename.map { "\"\($0)\"" } ?? "null"),
            "lineno": \(linenoJSON),
            "colno": \(colnoJSON)
        }
        """
        sut = try decodeWebViewMessage(ErrorMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
