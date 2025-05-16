// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import CapturePassable
import CaptureTestBridge
import Foundation
import XCTest

extension UploadedLog {
    /// Captures the next log received by the test API server. This returns immediately
    /// if the test server has logs pending, or blocks for up to 5s while while waiting
    /// for a log to be uploaded.
    ///
    /// - returns: The captured log.
    static func captureNextLog() -> UploadedLog? {
        let log = UploadedLog()

        guard next_uploaded_log(log) else {
            return nil
        }

        return log
    }

    func assertFieldsEqual(_ fields: [String: any Encodable]) {
        let fields = fields.map(UploadedField.fromKeyValue).sorted(by: {
            $0.key < $1.key
        })

        var sortedSelf = self.fields.sorted(by: { $0.key < $1.key })
        sortedSelf.removeAll { ["_file", "_line", "_function"].contains($0.key) }

        XCTAssertEqual(fields, sortedSelf)
    }

    func field(withKey: String) -> UploadedField? {
        self.fields.first(where: { $0.key == withKey })
    }
}

extension Field {
    override public var description: String {
        return "(\(self.key), \(self.data as? String ?? (self.data as? Data)?.base64EncodedString() ?? "nil")"
    }
}

private struct MockEncodable: Encodable {
    struct Error: Swift.Error {}

    func encode(to _: Encoder) throws {
        throw Error()
    }
}

final class CaptureE2ENetworkTests: BaseNetworkingTestCase {
    var logger: Logger!
    var storage: StorageProvider!

    private func setUpLogger() throws -> Logger {
        self.storage = MockStorageProvider()

        let apiURL = self.setUpTestServer()
        let logger = try XCTUnwrap(
            Logger(
                withAPIKey: "test!",
                bufferDirectory: self.setUpSDKDirectory(),
                apiURL: apiURL,
                remoteErrorReporter: MockRemoteErrorReporter(),
                configuration: .init(),
                sessionStrategy: SessionStrategy.fixed(sessionIDGenerator: { "mock-group-id" }),
                dateProvider: MockDateProvider(),
                fieldProviders: [
                    MockFieldProvider(
                        getFieldsClosure: {
                            [
                                "field_provider": "mock_field_provider",
                                "failing_to_convert_and_should_be_skipped_field": MockEncodable(),
                            ]
                        }
                    ),
                ],
                storageProvider: self.storage,
                timeProvider: SystemTimeProvider()
            )
        )

        self.logger = logger

        return logger
    }

    func testSessionReplay() async throws {
        _ = try self.setUpLogger()

        let streamID = try await nextApiStream()
        configure_aggressive_continuous_uploads(streamID)

        let logs = [
            try XCTUnwrap(UploadedLog.captureNextLog()),
            try XCTUnwrap(UploadedLog.captureNextLog()),
            try XCTUnwrap(UploadedLog.captureNextLog()),
        ]

        // The first 3 logs are sdk configuration, session replay, and resource utilization logs but
        // their order is non-deterministic.
        XCTAssertTrue(logs.contains { log in
            log.message == "SDKConfigured" && log.sessionID == "mock-group-id"
        })
        XCTAssertTrue(logs.contains { log in
            log.message.isEmpty && log.sessionID == "mock-group-id"
        })
        XCTAssertTrue(logs.contains { log in
            log.message == "Screen captured"
                && log.logType == Capture.Logger.LogType.replay.rawValue
                && log.logLevel == UInt32(LogLevel.info.rawValue)
                // Screen capture is included in a binary field.
                && log.field(withKey: "screen")?.type == .data
        })
    }

    // swiftlint:disable:next function_body_length
    func testLoggerE2E() async throws {
        // Use the default logger configuration.
        let appStateAttributes = AppStateAttributes()
        let clientAttributes = ClientAttributes()
        let deviceAttributes = DeviceAttributes()
        let networkAttributes = NetworkAttributes()

        deviceAttributes.start()
        networkAttributes.start(with: MockCoreLogging())

        let logger = try self.setUpLogger()

        // Add a valid field, it should be present.
        logger.addField(withKey: "foo", value: "value_foo")

        // Add and override fields, only its latest version should be present.
        logger.addField(withKey: "bar", value: "value_to_be_overridden")
        logger.addField(withKey: "bar", value: "value_bar")

        // Add and remove field - it should not be present.
        logger.addField(withKey: "car", value: "value_car")
        logger.removeField(withKey: "car")

        // Add a field prefixed with "_", it should be dropped and not present.
        logger.addField(withKey: "_dar", value: "value_dar")

        let streamID = try await nextApiStream()
        configure_aggressive_continuous_uploads(streamID)

        // The first 3 logs are sdk configuration, session replay, and resource utilization logs but
        // their order is non-deterministic.
        let logs: [UploadedLog] = [
            try XCTUnwrap(.captureNextLog()),
            try XCTUnwrap(.captureNextLog()),
            try XCTUnwrap(.captureNextLog()),
        ]

        XCTAssertTrue(logs.contains { log in
            log.message == "SDKConfigured" && log.sessionID == "mock-group-id"
        })
        XCTAssertTrue(logs.contains { log in
            return log.logLevel == UInt32(LogLevel.debug.rawValue)
                && log.message.isEmpty
                && log.sessionID == "mock-group-id"
        })
        XCTAssertTrue(logs.contains { log in
            return log.logLevel == UInt32(LogLevel.info.rawValue)
                && log.message == "Screen captured"
                && log.sessionID == "mock-group-id"
        })

        // TODO(Augustyniak): Do `replayScreenshotLog.hasFields` in here after figuring out how to figure out
        // what the expected value for "screen" key should be (depends on the simulator type).
        let invalidUTF8String = ("abc💇‍♀️" as NSString).substring(with: NSRange(location: 0, length: 4))

        logger.log(level: .debug, message: "hello world", fields: ["invalid_utf": invalidUTF8String],
                   error: nil)

        let helloWorldLog: UploadedLog = try XCTUnwrap(.captureNextLog())

        XCTAssertEqual(helloWorldLog.logLevel, UInt32(LogLevel.debug.rawValue))
        XCTAssertEqual(helloWorldLog.logType, Capture.Logger.LogType.normal.rawValue)
        XCTAssertEqual(helloWorldLog.message, "hello world")
        XCTAssertEqual(helloWorldLog.sessionID, "mock-group-id")

        let defaultFields = appStateAttributes.getFields()
            .mergedOverwritingConflictingKeys(clientAttributes.getFields())
            .mergedOverwritingConflictingKeys(deviceAttributes.getFields())
            .mergedOverwritingConflictingKeys(networkAttributes.getFields())

        let helloWorldExpectedFields: [String: Encodable] = [
            "bar": "value_bar",
            "field_provider": "mock_field_provider",
            "foo": "value_foo",
            "invalid_utf": "abc?",
        ].mergedOverwritingConflictingKeys(defaultFields)

        helloWorldLog.assertFieldsEqual(helloWorldExpectedFields)

        let fields: [String: Encodable] = [
            "field": "passed_in",
            "screen_replay": try XCTUnwrap(
                SessionReplayCapture(data: try XCTUnwrap(
                    "hello".data(using: .utf8)
                ))
            ),
            "data_field": try XCTUnwrap("test".data(using: .utf8)),
            "date_field": Date(timeIntervalSince1970: 0),
            "failing_to_convert_and_should_be_skipped_field": MockEncodable(),
        ]
        logger.log(level: .debug, message: "second log", fields: fields)

        let secondLog: UploadedLog = try XCTUnwrap(.captureNextLog())

        let expectedFields: [String: Encodable] = [
            "bar": "value_bar",
            "field_provider": "mock_field_provider",
            "field": "passed_in",
            "foo": "value_foo",
            "screen_replay": try XCTUnwrap("hello".data(using: .utf8)),
            "data_field": "dGVzdA==",
            "date_field": "1970-01-01T00:00:00.000Z",
        ].mergedOverwritingConflictingKeys(defaultFields)

        XCTAssertEqual(secondLog.logType, Capture.Logger.LogType.normal.rawValue)
        XCTAssertEqual(secondLog.logLevel, UInt32(LogLevel.debug.rawValue))
        XCTAssertEqual(secondLog.message, "second log")
        XCTAssertEqual(secondLog.sessionID, "mock-group-id")
        secondLog.assertFieldsEqual(expectedFields)

        // Issue a second log and verify that we see it uploaded. This explicitly validates
        // that the recursion check is cleared out between log calls. Here we also verify
        // that a non-default log type is plumbed through.
        logger.log(
            level: .debug,
            message: "alternate type log",
            fields: fields,
            error: nil,
            type: Capture.Logger.LogType.device,
            blocking: false
        )

        let thirdLog: UploadedLog = try XCTUnwrap(.captureNextLog())

        XCTAssertEqual(thirdLog.logType, Capture.Logger.LogType.device.rawValue)
        XCTAssertEqual(thirdLog.logLevel, UInt32(LogLevel.debug.rawValue))
        XCTAssertEqual(thirdLog.message, "alternate type log")
        XCTAssertEqual(thirdLog.sessionID, "mock-group-id")

        thirdLog.assertFieldsEqual(expectedFields)
    }
}

private extension UploadedField {
    static func fromKeyValue(keyValue: (key: String, value: any Encodable)) -> UploadedField {
        let type: FieldType = if keyValue.value is NSString {
            .string
        } else if keyValue.value is NSData {
            .data
        } else {
            {
                assertionFailure("unknown value type \(keyValue.value)")
                return .data
            }()
        }

        return UploadedField(key: keyValue.key, value: keyValue.value as AnyObject, type: type)
    }
}
