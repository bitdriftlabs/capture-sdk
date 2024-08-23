// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class SpanTests: XCTestCase {
    func testStartEndSpan() throws {
        let timeProvider = MockTimeProvider()
        let logger = MockCoreLogging()
        // Scope the creation of the span to confirm that the span is not ended with 'unknown' result
        // if it has been manually ended prior to its deinit.
        do {
            let span = Span(
                logger: logger,
                name: "test",
                level: .debug,
                file: nil,
                line: nil,
                function: nil,
                fields: [
                    "test_key": "test_value",
                    "_span_id": "should be ignored",
                ],
                timeProvider: timeProvider
            )

            XCTAssertEqual(1, logger.logs.count)
            let startLog = logger.logs[0]

            let emittedStartFields = startLog.fields
            XCTAssertEqual([
                "_span_id": span.id.uuidString,
                "_span_type": "start",
                "_span_name": "test",
                "test_key": "test_value",
            ], emittedStartFields as? [String: String])

            timeProvider.advanceBy(timeInterval: 1.0)

            span.end(
                .success,
                fields: [
                    "test_key": "new_value",
                    "_span_id": "should be ignored",
                ]
            )
            XCTAssertEqual(2, logger.logs.count)
            let endLog = logger.logs[1]

            let emittedEndFields = endLog.fields
            XCTAssertEqual([
                "_duration_ms": "1000.0",
                "_span_id": span.id.uuidString,
                "_span_type": "end",
                "_span_name": "test",
                "_result": "success",
                "test_key": "new_value",
            ], emittedEndFields as? [String: String])
        }

        XCTAssertEqual(2, logger.logs.count)
    }

    func testSpanEmitsUnknownOnDeinit() throws {
        let timeProvider = MockTimeProvider()
        let logger = MockCoreLogging()

        // Scope the creation of the span to confirm that the span is emits 'unknown' result it it's not
        // ended manually and is deinited.
        let spanID: String = {
            let span = Span(
                logger: logger,
                name: "test",
                level: .debug,
                file: nil,
                line: nil,
                function: nil,
                fields: ["test_key": "test_value"],
                timeProvider: timeProvider
            )

            XCTAssertEqual(1, logger.logs.count)
            let startLog = logger.logs[0]

            let emittedStartFields = startLog.fields
            XCTAssertEqual([
                "_span_id": span.id.uuidString,
                "_span_type": "start",
                "_span_name": "test",
                "test_key": "test_value",
            ], emittedStartFields as? [String: String])

            timeProvider.advanceBy(timeInterval: 1.0)

            return span.id.uuidString
        }()

        XCTAssertEqual(2, logger.logs.count)
        let endLog = logger.logs[1]

        let emittedEndFields = endLog.fields
        XCTAssertEqual([
            "_duration_ms": "1000.0",
            "_span_id": spanID,
            "_span_type": "end",
            "_span_name": "test",
            "_result": "unknown",
            "test_key": "test_value",
        ], emittedEndFields as? [String: String])

        XCTAssertEqual(2, logger.logs.count)
    }
}
