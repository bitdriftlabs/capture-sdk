// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class CustomLogMessageTests: XCTestCase {
    private var sut: CustomLogMessage!

    func testMakeLoggingActionWithInfoLevel() throws {
        try givenCustomLogMessage(level: "info")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .info)
    }

    func testMakeLoggingActionWithWarnLevel() throws {
        try givenCustomLogMessage(level: "warn")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .warning)
    }

    func testMakeLoggingActionWithErrorLevel() throws {
        try givenCustomLogMessage(level: "error")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .error)
    }

    func testMakeLoggingActionWithTraceLevel() throws {
        try givenCustomLogMessage(level: "trace")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .trace)
    }

    func testMakeLoggingActionWithUnknownLevelDefaultsToDebug() throws {
        try givenCustomLogMessage(level: "verbose")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .debug)
    }

    func testMakeLoggingActionIsCaseInsensitiveForLevel() throws {
        try givenCustomLogMessage(level: "INFO")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .info)
    }

    func testMakeLoggingActionMergesCustomFieldsAsStrings() throws {
        try givenCustomLogMessage(fields: [
            "userId": .string("abc123"),
            "retryCount": .number(3),
            "isPremium": .bool(true),
            "nickname": .null,
        ])
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .debug) { fields in
            XCTAssertEqual(fields["userId"], "abc123")
            XCTAssertEqual(fields["retryCount"], "3.0")
            XCTAssertEqual(fields["isPremium"], "true")
            XCTAssertEqual(fields["nickname"], "null")
        }
    }

    func testMakeLoggingActionMergesNestedObjectFieldAsJSONString() throws {
        try givenCustomLogMessage(fields: [
            "context": .object(["a": .string("x"), "b": .number(1)]),
        ])
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .debug) { fields in
            XCTAssertEqual(fields["context"], "{\"a\":\"x\",\"b\":1}")
        }
    }

    func testMakeLoggingActionWithoutFieldsUsesBaseFieldsOnly() throws {
        try givenCustomLogMessage(fields: nil)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "custom message", level: .debug) { fields in
            XCTAssertEqual(fields["_source"], "webview")
            XCTAssertNotNil(fields["_timestamp"])
        }
    }
}

private extension CustomLogMessageTests {
    func givenCustomLogMessage(
        level: String = "debug",
        message: String = "custom message",
        fields: WebViewSerializableFields? = nil
    ) throws {
        let fieldsJSON = try fields.map { try encodeToJSON($0) } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "customLog",
            "timestamp": 1700000000000,
            "level": "\(level)",
            "message": "\(message)",
            "fields": \(fieldsJSON)
        }
        """
        sut = try decodeWebViewMessage(CustomLogMessage.self, from: json)
    }

    func encodeToJSON(_ fields: WebViewSerializableFields) throws -> String {
        let data = try JSONEncoder().encode(fields)
        return try XCTUnwrap(String(data: data, encoding: .utf8))
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
