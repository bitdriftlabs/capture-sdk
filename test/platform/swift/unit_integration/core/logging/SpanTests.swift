// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import Foundation
import XCTest

final class SpanTests: XCTestCase {
    // We need to allow a fair bit of variance due to potential CI load affecting the simulator
    private let allowedTimeVarianceSeconds: TimeInterval = 0.200

    private func createSpan(logger: MockCoreLogging, timeProvider: TimeProvider = MockTimeProvider(),
                            start: TimeInterval? = nil, parent: UUID? = nil) -> Span
    {
        Span(
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
            timeProvider: timeProvider,
            customStartTimeInterval: start,
            parentSpanID: parent
        )
    }

    private func assertTimeWithinRange(timeStringMS: String, intervalInSeconds: TimeInterval) {
        let time = Double(timeStringMS)! / 1000.0
        let minValue = (intervalInSeconds - allowedTimeVarianceSeconds) < 0 ? 0 : intervalInSeconds - allowedTimeVarianceSeconds
        let maxValue = intervalInSeconds + allowedTimeVarianceSeconds
        XCTAssertGreaterThanOrEqual(time, minValue)
        XCTAssertLessThanOrEqual(time, maxValue)
    }

    func testStartEndSpan() throws {
        let timeProvider = MockTimeProvider()
        let logger = MockCoreLogging()
        // Scope the creation of the span to confirm that the span is not ended with 'unknown' result
        // if it has been manually ended prior to its deinit.
        do {
            let span = self.createSpan(logger: logger, timeProvider: timeProvider)

            XCTAssertEqual(1, logger.logs.count)
            let startLog = logger.logs[0]

            let emittedStartFields = startLog.fields
            XCTAssertEqual([
                "_span_id": span.id.uuidString,
                "_span_type": "start",
                "_span_name": "test",
                "test_key": "test_value",
            ], emittedStartFields as? [String: String])

            Thread.sleep(forTimeInterval: 1.0)

            span.end(
                .success,
                fields: [
                    "test_key": "new_value",
                    "_span_id": "should be ignored",
                ]
            )
            XCTAssertEqual(2, logger.logs.count)
            let endLog = logger.logs[1]

            var emittedEndFields = endLog.fields
            assertTimeWithinRange(timeStringMS: emittedEndFields!["_duration_ms"] as! String, intervalInSeconds: 1.0)
            emittedEndFields?.removeValue(forKey: "_duration_ms")
            XCTAssertEqual([
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
            let span = self.createSpan(logger: logger, timeProvider: timeProvider)
            return span.id.uuidString
        }()

        XCTAssertEqual(2, logger.logs.count)
        let endLog = logger.logs[1]

        var emittedEndFields = endLog.fields
        assertTimeWithinRange(timeStringMS: emittedEndFields!["_duration_ms"] as! String, intervalInSeconds: 0.0)
        emittedEndFields?.removeValue(forKey: "_duration_ms")
        XCTAssertEqual([
            "_span_id": spanID,
            "_span_type": "end",
            "_span_name": "test",
            "_result": "unknown",
            "test_key": "test_value",
        ], emittedEndFields as? [String: String])

        XCTAssertEqual(2, logger.logs.count)
    }

    func testStartEndSpanWithCustomInterval() throws {
        let logger = MockCoreLogging()

        // Test start and end
        let span = self.createSpan(logger: logger, start: 0)
        span.end(
            .success,
            fields: ["_span_id": "should be ignored"],
            endTimeInterval: 1
        )
        XCTAssertEqual(2, logger.logs.count)
        let startLog = logger.logs[0]
        let endLog = logger.logs[1]

        let emittedEndFields = endLog.fields
        XCTAssertEqual([
            "_duration_ms": "1000.0",
            "_span_id": span.id.uuidString,
            "test_key": "test_value",
            "_span_type": "end",
            "_span_name": "test",
            "_result": "success",
        ], emittedEndFields as? [String: String])

        XCTAssertEqual(2, logger.logs.count)
        XCTAssertEqual(Date(timeIntervalSince1970: 0), startLog.occurredAtOverride)
        XCTAssertEqual(Date(timeIntervalSince1970: 1), endLog.occurredAtOverride)

        // Test start with no end
        let timeProvider = MockTimeProvider()
        let spanOnlyStart = self.createSpan(logger: logger, timeProvider: timeProvider, start: 0)
        Thread.sleep(forTimeInterval: 1.0)
        spanOnlyStart.end(.success)

        XCTAssertEqual(4, logger.logs.count)
        assertTimeWithinRange(timeStringMS: (logger.logs[3].fields?["_duration_ms"] as? String)!, intervalInSeconds: 1.0)
        XCTAssertEqual(Date(timeIntervalSince1970: 0), logger.logs[2].occurredAtOverride)
        XCTAssertEqual(nil, logger.logs[3].occurredAtOverride)

        // Test end with no start
        let spanOnlyEnd = self.createSpan(logger: logger, timeProvider: timeProvider)
        Thread.sleep(forTimeInterval: 1.0)
        spanOnlyEnd.end(.success, endTimeInterval: 666)

        XCTAssertEqual(6, logger.logs.count)
        assertTimeWithinRange(timeStringMS: (logger.logs[5].fields?["_duration_ms"] as? String)!, intervalInSeconds: 1.0)
        XCTAssertEqual(nil, logger.logs[4].occurredAtOverride)
        XCTAssertEqual(Date(timeIntervalSince1970: 666), logger.logs[5].occurredAtOverride)
    }

    func testSpanWithParents() throws {
        let logger = MockCoreLogging()
        let span = self.createSpan(logger: logger, parent: UUID())
        span.end(.success, endTimeInterval: 0.000133)

        XCTAssertEqual(2, logger.logs.count)

        let startLogParent = logger.logs[0].fields?["_span_parent_id"] as? String
        let endLogParent = logger.logs[1].fields?["_span_parent_id"] as? String

        XCTAssertEqual(try XCTUnwrap(startLogParent), try XCTUnwrap(span.parentSpanID?.uuidString))
        XCTAssertEqual(try XCTUnwrap(endLogParent), try XCTUnwrap(span.parentSpanID?.uuidString))
    }
}
