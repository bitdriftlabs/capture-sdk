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

final class CaptureE2ENetworkTests: XCTestCase {
    var logger: Logger!
    var storage: StorageProvider!
    private var server: TestApiServer!

    override func tearDown() {
        self.server = nil
    }

    private func setUpLogger(fieldProviders: [FieldProvider]? = nil) throws -> Logger {
        self.storage = MockStorageProvider()

        // Use instance-based server for test isolation
        self.server = TestApiServer()

        let logger = try XCTUnwrap(
            Logger(
                withAPIKey: "test!",
                remoteErrorReporter: MockRemoteErrorReporter(),
                configuration: .init(apiURL: self.server.baseURL, rootFileURL: LoggerTestFixture.makeSDKDirectory()),
                sessionStrategy: SessionStrategy.fixed(sessionIDGenerator: { "mock-group-id" }),
                dateProvider: MockDateProvider(),
                fieldProviders: fieldProviders ?? [
                    MockFieldProvider(
                        getFieldsClosure: {
                            [
                                "field_provider": "mock_field_provider",
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

        let streamID = await self.server.nextStream()
        XCTAssertNotEqual(streamID, -1, "Timed out waiting for API stream")
        await self.server.configureAggressiveUploads(streamId: streamID)

        // Collect logs until we've seen all expected initial logs.
        // The SDK generates SDKConfigured, resource utilization, and screen capture logs
        // but the exact count may vary.
        let logs = await self.server.collectLogsMatching([
            { $0.message == "SDKConfigured" && $0.sessionID == "mock-group-id" },
            { $0.message.isEmpty && $0.sessionID == "mock-group-id" },
            {
                $0.message == "Screen captured"
                    && $0.logType == Capture.Logger.LogType.replay.rawValue
                    && $0.logLevel == UInt32(LogLevel.info.rawValue)
                    && $0.field(withKey: "screen")?.type == .data
            },
        ])

        XCTAssertEqual(logs.count, 3, "Did not find all expected initial logs")
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

        let streamID = await self.server.nextStream()
        XCTAssertNotEqual(streamID, -1, "Timed out waiting for API stream")
        await self.server.configureAggressiveUploads(streamId: streamID)

        // TODO(Augustyniak): Do `replayScreenshotLog.hasFields` in here after figuring out how to figure out
        // what the expected value for "screen" key should be (depends on the simulator type).
        let invalidUTF8String = ("abc💇‍♀️" as NSString).substring(with: NSRange(location: 0, length: 4))

        logger.log(level: .debug, message: "hello world", fields: ["invalid_utf": invalidUTF8String],
                   error: nil)

        // Drain logs until we find the "hello world" log. The SDK generates several initial logs
        // (SDKConfigured, resource utilization, screen captures) whose count can vary.
        let helloWorldLogOpt = await self.server.nextUploadedLogMatching { $0.message == "hello world" }
        let helloWorldLog: UploadedLog = try XCTUnwrap(helloWorldLogOpt, "Did not receive 'hello world' log")

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

        // Find "second log" - there may be other logs interleaved
        let secondLogOpt = await self.server.nextUploadedLogMatching { $0.message == "second log" }
        let secondLog: UploadedLog = try XCTUnwrap(secondLogOpt, "Did not receive 'second log'")

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

        // Find "alternate type log" - there may be other logs interleaved
        let thirdLogOpt = await self.server.nextUploadedLogMatching { $0.message == "alternate type log" }
        let thirdLog: UploadedLog = try XCTUnwrap(thirdLogOpt, "Did not receive 'alternate type log'")

        XCTAssertEqual(thirdLog.logType, Capture.Logger.LogType.device.rawValue)
        XCTAssertEqual(thirdLog.logLevel, UInt32(LogLevel.debug.rawValue))
        XCTAssertEqual(thirdLog.message, "alternate type log")
        XCTAssertEqual(thirdLog.sessionID, "mock-group-id")

        thirdLog.assertFieldsEqual(expectedFields)
    }

    func testFieldProviderEncodingFailureIsHandledGracefully() async throws {
        // Set up logger with a field provider that includes a failing encodable
        let fieldProviders: [FieldProvider] = [
            MockFieldProvider(
                getFieldsClosure: {
                    [
                        "valid_field": "valid_value",
                        "failing_field": MockEncodable(),
                    ]
                }
            ),
        ]

        _ = try self.setUpLogger(fieldProviders: fieldProviders)

        let streamID = await self.server.nextStream()
        XCTAssertNotEqual(streamID, -1, "Timed out waiting for API stream")
        await self.server.configureAggressiveUploads(streamId: streamID)

        self.logger.log(level: .debug, message: "test field provider failure")

        let log = await self.server.nextUploadedLogMatching { $0.message == "test field provider failure" }
        let uploadedLog = try XCTUnwrap(log, "Did not receive test log")

        // Verify valid field from provider is included
        XCTAssertNotNil(uploadedLog.field(withKey: "valid_field"))
        XCTAssertEqual(uploadedLog.field(withKey: "valid_field")?.value as? String, "valid_value")

        // Verify failing field is excluded (not present, not null)
        XCTAssertNil(uploadedLog.field(withKey: "failing_field"))
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
