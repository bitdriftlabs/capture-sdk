// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import CapturePassable
import Foundation
import XCTest

final class LoggerTests: XCTestCase {
    func testPropertiesReturnsCorrectValues() throws {
        let logger = try Logger.testLogger(withAPIKey: "test_api_key")

        XCTAssertEqual(36, logger.sessionID.count)
        XCTAssertEqual(36, logger.deviceID.count)
    }

    // Basic test to ensure we can create a logger and call log.
    func testLogger() throws {
        let logger = try Logger.testLogger(withAPIKey: "test_api_key")

        logger.log(level: .debug, message: "test with fields", fields: ["hello": "world"], type: .normal)
        logger.log(level: .debug, message: "test nil fields", fields: nil, type: .normal)
        logger.log(level: .debug, message: "test no fields", type: .normal)
    }

    // Verifies that we don't end up recursing forever (resulting in a stack overflow) when a provider ends
    // up calling back into the logger.
    func testPreventsLoggingReEntryFromWithinRegisteredProviders() throws {
        var logger: Logger?

        let expectation = self.expectation(description: "'SDKConfigured' log is emitted")
        expectation.expectedFulfillmentCount = 1

        let fieldProvider = MockFieldProvider { [weak logger] in
            logger?.log(level: .debug, message: "never_logged", type: .normal)
            expectation.fulfill()
            return [:]
        }

        logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            fieldProviders: [fieldProvider]
        )

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))
    }

    func testRunsProvidersOffCallerThread() throws {
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

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionStrategy: SessionStrategy.fixed(),
            dateProvider: dateProvider,
            fieldProviders: [fieldProvider]
        )

        let expectations = [
            dateProviderExpectation,
            fieldProviderExpectation,
        ]

        logger.log(level: .debug, message: "logged", type: .normal)

        XCTAssertEqual(.completed, XCTWaiter().wait(for: expectations, timeout: 1))
    }

    func testFailingEncodableField() throws {
        struct FailingEncodable: Encodable {
            struct Error: Swift.Error {}

            func encode(to _: Encoder) throws {
                throw Error()
            }
        }

        let bridge = MockLoggerBridging()

        let logger = try Logger.testLogger(
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

    func testSleepModeChangeFiresOverBridge() throws {
        let bridge = MockLoggerBridging()
        let logger = try Logger.testLogger(
            withAPIKey: "some_key",
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        logger.setSleepMode(.enabled)
        XCTAssertEqual(bridge.sleepMode, .enabled)
    }

    func testErrorLogging() throws {
        struct Error: Swift.Error {}

        let bridge = MockLoggerBridging()

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
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

        var logFields = try XCTUnwrap(log.fields?.toDictionary())
        XCTAssertNotNil(logFields.removeValue(forKey: "_file"))
        XCTAssertNotNil(logFields.removeValue(forKey: "_line"))
        XCTAssertNotNil(logFields.removeValue(forKey: "_function"))

        XCTAssertEqual(log.level, .debug)
        XCTAssertEqual(log.message, "test")
        self.assertEqual(
            [
                "_error": error.localizedDescription,
                "_error_details": String(describing: error),
                "foo": "bar",
            ],
            logFields
        )
    }

    func testErrorLoggingStringConvertibleField() throws {
        struct BonusContext: CustomStringConvertible {
            var description: String = "bonus!"
        }

        let bridge = MockLoggerBridging()

        let logger = try Logger.testLogger(
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )

        let error = NSError(domain: "com.example.err", code: 4099, userInfo: [
            "ctx": BonusContext(),
            "more": "extra stuff",
        ])
        logger.logError("something went wrong", error: error)

        XCTAssertEqual(bridge.errors.count, 0)
        XCTAssertEqual(bridge.logs.count, 1)

        let log = bridge.logs[0]
        var logFields = try XCTUnwrap(log.fields?.toDictionary())
        XCTAssertNotNil(logFields.removeValue(forKey: "_file"))
        XCTAssertNotNil(logFields.removeValue(forKey: "_line"))
        XCTAssertNotNil(logFields.removeValue(forKey: "_function"))

        XCTAssertEqual(log.level, .error)
        XCTAssertEqual(log.message, "something went wrong")
        self.assertEqual(
            [
                "_error": error.localizedDescription,
                "_error_details": String(describing: error),
                "_error_info_more": "extra stuff",
                "_error_info_ctx": "bonus!",
            ],
            logFields
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

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        logger.log(requestInfo)

        XCTAssertEqual(bridge.logs.count, 1)
        let log = bridge.logs[0]

        var logFields = try log.fields?.toDictionary()
        XCTAssertNotNil(logFields?.removeValue(forKey: "_file"))
        XCTAssertNotNil(logFields?.removeValue(forKey: "_line"))
        XCTAssertNotNil(logFields?.removeValue(forKey: "_function"))

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
        ], logFields)
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

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        logger.log(responseInfo)

        XCTAssertEqual(bridge.logs.count, 1)
        let log = bridge.logs[0]

        var logFields = try log.fields?.toDictionary()
        XCTAssertNotNil(logFields?.removeValue(forKey: "_file"))
        XCTAssertNotNil(logFields?.removeValue(forKey: "_line"))
        XCTAssertNotNil(logFields?.removeValue(forKey: "_function"))

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
        ], logFields)
        XCTAssertEqual([
            "_request.foo": "bar",
            "_request._host": "api.bitdrift.io",
            "_request._method": "POST",
            "_request._path": "/ping/12345",
            "_request._span_id": requestInfo.spanID,
            "_request._query": "bar",
        ], try log.matchingFields?.toDictionary())
    }

    func testSDKDirectoryPermissions() throws {
        func protection(at path: String) throws -> URLFileProtection? {
            let url = NSURL(fileURLWithPath: path)
            var fileProtection: AnyObject?
            try url.getResourceValue(&fileProtection, forKey: .fileProtectionKey)
            return fileProtection as? URLFileProtection
        }

        // Create root path with complete protection
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        let existingWithNone = root.appendingPathComponent("existingWithNone")
        try! FileManager.default.createDirectory(at: existingWithNone,
                                                 withIntermediateDirectories: true)

        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try! (root as NSURL).setResourceValue(URLFileProtection.complete,
                                              forKey: .fileProtectionKey)

        // Create a new directory inside the root path (should inherit complete protection)
        let existingWithComplete = root.appendingPathComponent("existingWithComplete")
        try! FileManager.default.createDirectory(at: existingWithComplete,
                                                 withIntermediateDirectories: true)

        // Create a buffer directory inside the root path (should inherit complete protection)
        let buffers = root.appendingPathComponent("buffers")
        try! FileManager.default.createDirectory(at: buffers,
                                                 withIntermediateDirectories: true)

        // Create a file under buffers to pretend like it's the ring buffer
        let bufferFile = buffers.appendingPathComponent("bufferFile")
        FileManager.default.createFile(atPath: bufferFile.path,
                                       contents: "foobar".data(using: .ascii))
        try! (bufferFile as NSURL).setResourceValue(URLFileProtection.complete, forKey: .fileProtectionKey)

        // Test to see if disable protection works on a new path
        XCTAssertEqual(.complete, try! protection(at: root.path))
        let newPath = root.appendingPathComponent("newPath")
        XCTAssertFalse(FileManager.default.fileExists(atPath: newPath.path))
        try! makeDirectoryAndDisableProtection(at: newPath.path)
        XCTAssertEqual(.completeUntilFirstUserAuthentication,
                       try! protection(at: newPath.path))

        // Test to see if disable protection works on an existing path with complete
        XCTAssertEqual(.complete, try! protection(at: existingWithComplete.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: existingWithComplete.path))
        try! makeDirectoryAndDisableProtection(at: existingWithComplete.path)
        XCTAssertEqual(.completeUntilFirstUserAuthentication,
                       try! protection(at: existingWithComplete.path))

        // Test to see if disable protection works on an existing path with none
        XCTAssertNil(try! protection(at: existingWithNone.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: existingWithNone.path))
        try! makeDirectoryAndDisableProtection(at: existingWithNone.path)
        XCTAssertNil(try! protection(at: existingWithNone.path))

        // Test to see if disable protection works on all files under buffers
        XCTAssertEqual(.complete, try! protection(at: bufferFile.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: bufferFile.path))
        try! makeDirectoryAndDisableProtection(at: root.path)
        XCTAssertEqual(.completeUntilFirstUserAuthentication,
                       try! protection(at: bufferFile.path))
    }
}

extension [Field] {
    func toDictionary() throws -> [String: String] {
        Dictionary(try self.map { field in
            (field.key, try XCTUnwrap(field.data as? String))
        }, uniquingKeysWith: { old, _ in old })
    }
}
