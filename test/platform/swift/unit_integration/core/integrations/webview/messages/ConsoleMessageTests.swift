// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class ConsoleMessageTests: XCTestCase {
    private var sut: ConsoleMessage!

    func testMakeLoggingActionWithErrorLevelLogsAtErrorLevel() throws {
        try givenConsoleMessage(level: "error")
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .error)
    }

    func testMakeLoggingActionWithWarnLevelLogsAtWarningLevel() throws {
        try givenConsoleMessage(level: "warn")
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .warning)
    }

    func testMakeLoggingActionWithInfoLevelLogsAtInfoLevel() throws {
        try givenConsoleMessage(level: "info")
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .info)
    }

    func testMakeLoggingActionWithUnknownLevelLogsAtDebugLevel() throws {
        try givenConsoleMessage(level: "trace")
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .debug)
    }

    func testMakeLoggingActionIncludesMessageAndLevelFields() throws {
        try givenConsoleMessage(level: "info", message: "hello world")
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .info) { fields in
            XCTAssertEqual(fields["_level"], "info")
            XCTAssertEqual(fields["_message"], "hello world")
        }
    }

    func testMakeLoggingActionJoinsUpToFiveArgs() throws {
        try givenConsoleMessage(args: ["a", "b", "c", "d", "e", "f"])
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .debug) { fields in
            XCTAssertEqual(fields["_args"], "a, b, c, d, e")
        }
    }

    func testMakeLoggingActionWithoutArgsOmitsArgsField() throws {
        try givenConsoleMessage(args: nil)
        let action = whenMakingLoggingAction()
        thenActionLogsConsole(action, level: .debug) { fields in
            XCTAssertNil(fields["_args"])
        }
    }
}

private extension ConsoleMessageTests {
    func givenConsoleMessage(
        level: String = "debug",
        message: String = "log message",
        args: [String]? = nil
    ) throws {
        let argsJSON = args.map { "[\($0.map { "\"\($0)\"" }.joined(separator: ","))]" } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "console",
            "timestamp": 1700000000000,
            "level": "\(level)",
            "message": "\(message)",
            "args": \(argsJSON)
        }
        """
        sut = try decodeWebViewMessage(ConsoleMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }

    func thenActionLogsConsole(
        _ action: WebViewLoggingAction?,
        level: LogLevel,
        fields assertFields: ([String: String]) -> Void = { _ in }
    ) {
        assertWebLogAction(action, message: "webview.console", level: level, fields: assertFields)
    }
}
