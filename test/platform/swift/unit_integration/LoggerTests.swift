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

final class LoggerTests: XCTestCase {
    func testPropertiesReturnsCorrectValues() {
        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(sessionReplayConfiguration: nil)
        )

        XCTAssertEqual(36, logger.sessionID.count)
        XCTAssertEqual(36, logger.deviceID.count)
    }

    // Basic test to ensure we can create a logger and call log.
    func testLogger() {
        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(sessionReplayConfiguration: nil)
        )

        logger.log(level: .debug, message: "test with fields", fields: ["hello": "world"], type: .normal)
        logger.log(level: .debug, message: "test nil fields", fields: nil, type: .normal)
        logger.log(level: .debug, message: "test no fields", type: .normal)
    }

    // Verifies that we don't end up recursing forever (resulting in a stack overflow) when a provider ends
    // up calling back into the logger.
    func testPreventsLoggingReEntryFromWithinRegisteredProviders() {
        var logger: Logger?

        let expectation = self.expectation(description: "'SDKConfigured' log is emitted")
        expectation.expectedFulfillmentCount = 1

        let fieldProvider = MockFieldProvider { [weak logger] in
            logger?.log(level: .debug, message: "never_logged", type: .normal)
            expectation.fulfill()
            return [:]
        }

        logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            fieldProviders: [fieldProvider],
            configuration: .init(sessionReplayConfiguration: nil)
        )

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))
    }

    func testRunsProvidersOffCallerThread() {
        let dateProvider = MockDateProvider()

        // Verify that test is executed on the main queue.
        dispatchPrecondition(condition: .onQueue(.main))

        let fieldProviderExpectation = self.expectation(
            description: "Field Provider is called on the background thread"
        )
        // Called once for OOTB "SDK configured" and once for custom emitted log.
        fieldProviderExpectation.expectedFulfillmentCount = 2

        let fieldProvider = MockFieldProvider {
            // Tests are evaluated on the main queue so we would expect this to run
            // on another thread if logs processing happens off the caller thread.
            dispatchPrecondition(condition: .notOnQueue(.main))
            fieldProviderExpectation.fulfill()
            return [:]
        }

        let dateProviderExpectation = self.expectation(
            description: "Date Provider is called on the background thread"
        )
        // Called once for OOTB "SDK configured" log and once for custom emitted log.
        dateProviderExpectation.expectedFulfillmentCount = 2

        dateProvider.getDateClosure = {
            // Tests are evaluated on the main queue so we would expect this to run
            // on another thread if logs processing happens off the caller thread.
            dispatchPrecondition(condition: .notOnQueue(.main))
            dateProviderExpectation.fulfill()
            return Date()
        }

        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            sessionStrategy: SessionStrategy.fixed(),
            dateProvider: dateProvider,
            fieldProviders: [fieldProvider],
            configuration: .init(sessionReplayConfiguration: nil)
        )

        let expectations = [
            dateProviderExpectation,
            fieldProviderExpectation,
        ]

        logger.log(level: .debug, message: "logged", type: .normal)

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 1))
    }

    func testFailingEncodableField() {
        struct FailingEncodable: Encodable {
            struct Error: Swift.Error {}

            func encode(to _: Encoder) throws {
                throw Error()
            }
        }

        let bridge = MockLoggerBridging()

        let logger = Logger.testLogger(
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )

        let fields: [String: Encodable] = [
            "foo": FailingEncodable(),
            "bar": "value_bar",
        ]
        logger.log(level: .debug, message: "test", fields: fields, type: .normal)

        XCTAssertEqual(bridge.errors.count, 1)
        XCTAssertEqual(bridge.errors[0].context, "write_log: failed to encode field")
    }

    func testErrorLogging() throws {
        struct Error: Swift.Error {}

        let bridge = MockLoggerBridging()

        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(sessionReplayConfiguration: .init(captureIntervalSeconds: 5)),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )

        let error = Error()
        logger.log(
            level: .debug,
            message: "test",
            fields: [
                "_error": "should_be_ignored",
                "foo": "bar",
            ],
            error: error,
            type: .normal
        )

        XCTAssertEqual(bridge.logs.count, 1)
        let log = bridge.logs[0]

        XCTAssertEqual(log.level, .debug)
        XCTAssertEqual(log.message, "test")
        self.assertEqual(
            [
                "_error": error.localizedDescription,
                "_error_details": String(describing: error),
                "foo": "bar",
            ],
            try XCTUnwrap(log.fields)
        )
    }

    func testLogRequest() throws {
        let requestInfo = HTTPRequestInfo(
            method: "POST",
            host: "api.bitdrift.io",
            path: .init(value: "/ping/12345"),
            query: "bar",
            extraFields: [
                "_query": "should_be_ignored",
                "foo": "bar",
            ]
        )

        let bridge = MockLoggerBridging()

        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(sessionReplayConfiguration: .init(captureIntervalSeconds: 5)),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        logger.log(requestInfo)

        XCTAssertEqual(bridge.logs.count, 1)
        let log = bridge.logs[0]

        XCTAssertEqual(log.message, "HTTPRequest")
        XCTAssertEqual(log.level, .debug)
        XCTAssertEqual(log.type, .span)
        XCTAssertEqual([
            "_host": "api.bitdrift.io",
            "_method": "POST",
            "_path": "/ping/12345",
            "_span_id": requestInfo.spanID,
            "_query": "bar",
            "foo": "bar",
        ], try XCTUnwrap(log.fields?.toDictionary()))
        XCTAssertTrue(try XCTUnwrap(log.matchingFields?.toDictionary()).isEmpty)
    }

    // swiftlint:disable:next function_body_length
    func testLogResponse() throws {
        let requestInfo = HTTPRequestInfo(
            method: "POST",
            host: "api.bitdrift.io",
            path: .init(value: "/ping/12345"),
            query: "bar",
            extraFields: [
                "_query": "should_be_ignored",
                "foo": "bar",
            ]
        )

        let response = HTTPResponse(
            result: .success,
            host: nil,
            path: nil,
            query: nil,
            statusCode: 200,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(requestInfo: requestInfo, response: response)

        let bridge = MockLoggerBridging()

        let logger = Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            configuration: .init(sessionReplayConfiguration: .init(captureIntervalSeconds: 5)),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        logger.log(responseInfo)

        XCTAssertEqual(bridge.logs.count, 1)
        let log = bridge.logs[0]

        XCTAssertEqual(log.message, "HTTPResponse")
        XCTAssertEqual(log.level, .debug)
        XCTAssertEqual(log.type, .span)
        XCTAssertEqual([
            "_duration_ms": "0",
            "_host": "api.bitdrift.io",
            "_method": "POST",
            "_path": "/ping/12345",
            "_result": "success",
            "_span_id": requestInfo.spanID,
            "_status_code": "200",
            "_query": "bar",
            "foo": "bar",
        ], try log.fields?.toDictionary())
        XCTAssertEqual([
            "_request.foo": "bar",
            "_request._host": "api.bitdrift.io",
            "_request._method": "POST",
            "_request._path": "/ping/12345",
            "_request._span_id": requestInfo.spanID,
            "_request._query": "bar",
        ], try log.matchingFields?.toDictionary())
    }
}

extension [Field] {
    func toDictionary() throws -> [String: String] {
        Dictionary(try self.map { field in
            (field.key, try XCTUnwrap(field.data as? String))
        }, uniquingKeysWith: { old, _ in old })
    }
}
