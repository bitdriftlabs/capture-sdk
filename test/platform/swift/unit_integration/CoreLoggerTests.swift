// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CapturePassable
import Foundation
import XCTest

final class CoreLoggerTests: XCTestCase {
    func testLog() {
        let bridge = MockLoggerBridging()
        let logger = CoreLogger(logger: bridge)
        logger.start()

        logger.log(level: .debug, message: "foo", type: .normal)

        XCTAssertEqual(1, bridge.logs.count)

        let log = bridge.logs[0]
        XCTAssertEqual("foo", log.message)
        XCTAssertNil(log.fields)
        XCTAssertEqual(.debug, log.level)
        XCTAssertEqual(.normal, log.type)
        XCTAssertFalse(log.blocking)
    }

    func testLogWithError() throws {
        struct MockError: Error {}

        let bridge = MockLoggerBridging()
        let logger = CoreLogger(logger: bridge)
        logger.start()

        let error = MockError()
        logger.log(level: .debug, message: "foo", error: error, type: .normal)

        XCTAssertEqual(1, bridge.logs.count)

        let log = bridge.logs[0]
        XCTAssertEqual("foo", log.message)
        self.assertEqual(
            [
                try Field.make(key: "_error", value: error.localizedDescription),
                try Field.make(key: "_error_details", value: String(describing: error)),
            ],
            log.fields
        )
        XCTAssertEqual(.debug, log.level)
        XCTAssertEqual(.normal, log.type)
        XCTAssertFalse(log.blocking)
    }

    func testLogInternal() {
        let bridge = MockLoggerBridging()
        let logger = CoreLogger(logger: bridge)
        logger.start()

        // Confirm that by default internal SDK logs are dropped.
        logger.logInternal(level: .debug, message: "foo")
        XCTAssertTrue(bridge.logs.isEmpty)

        bridge.mockRuntimeVariable(.internalLogs, with: true)

        // Confirm that internal SDK logs can be enabled with runtime value.
        logger.logInternal(level: .debug, message: "foo")
        XCTAssertEqual(1, bridge.logs.count)

        let log = bridge.logs[0]
        XCTAssertEqual("foo", log.message)
        // _file, _line and _function keys
        XCTAssertEqual(3, log.fields?.count)
        XCTAssertEqual(.debug, log.level)
        XCTAssertEqual(.internalsdk, log.type)
        XCTAssertFalse(log.blocking)
    }

    func testPassingFields() {
        let bridge = MockLoggerBridging()
        let logger = CoreLogger(logger: bridge)
        logger.start()

        logger.log(
            level: .debug,
            message: "foo",
            fields: ["foo": "bar"],
            matchingFields: ["matchingFoo": "matchingBar"],
            type: .normal
        )

        XCTAssertEqual(1, bridge.logs.count)
        let log = bridge.logs[0]

        self.assertEqual(
            [CapturePassable.Field(key: "foo", data: "bar" as AnyObject, type: .string)],
            log.fields
        )
        self.assertEqual(
            [CapturePassable.Field(key: "matchingFoo", data: "matchingBar" as AnyObject, type: .string)],
            log.matchingFields
        )
    }
}
