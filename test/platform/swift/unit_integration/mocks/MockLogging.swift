// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

/// Logging interface mock.
public final class MockLogging {
    public struct Log {
        enum Object {
            case message(String)
            case request(HTTPRequestInfo)
            case response(HTTPResponseInfo)
        }

        let level: LogLevel
        let object: Object
        public let fields: Fields?

        public func message() -> String? {
            if case let .message(message) = self.object {
                return message
            }

            return nil
        }

        public func request() -> HTTPRequestInfo? {
            if case let .request(request) = self.object {
                return request
            }

            return nil
        }

        public func response() -> HTTPResponseInfo? {
            if case let .response(response) = self.object {
                return response
            }

            return nil
        }
    }

    public var logExpectation: XCTestExpectation?
    public var logRequestExpectation: XCTestExpectation?
    public var logResponseExpectation: XCTestExpectation?

    public init() {}

    /// The logs emitted by the logger
    public private(set) var logs = [Log]()
    /// The number of logs emitted by the logger.
    public var logsCount: Int { self.logs.count }
    /// A closure that's called every time a log is emitted by the logger.
    public var onLog: (_ log: Log) -> Void = { _ in }
    /// The sleep mode state
    public private(set) var sleepMode: SleepMode = .inactive
}

extension MockLogging: Logging {
    public var sessionID: String { "fooID" }
    public var sessionURL: String { "fooURL" }

    public func startNewSession() {}

    public var deviceID: String { "deviceID" }

    public func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file _: String?,
        line _: Int?,
        function _: String?,
        fields: Fields?,
        error _: Error?
    ) {
        let log = Log(
            level: level,
            object: .message(message()),
            fields: fields
        )
        self.logs.append(log)
        self.logExpectation?.fulfill()
        self.onLog(log)
    }

    public func log(_ request: HTTPRequestInfo, file _: String?, line _: Int?, function _: String?) {
        let log = Log(
            level: .debug,
            object: .request(request),
            fields: request.toFields()
        )

        self.logs.append(log)
        self.logRequestExpectation?.fulfill()
        self.onLog(log)
    }

    public func log(_ response: HTTPResponseInfo, file _: String?, line _: Int?, function _: String?) {
        let log = Log(
            level: .debug,
            object: .response(response),
            fields: response.toFields()
        )

        self.logs.append(log)
        self.logResponseExpectation?.fulfill()
        self.onLog(log)
    }

    public func logAppLaunchTTI(_: TimeInterval) {}

    public func logScreenView(screenName _: String) {}

    public func addField(withKey _: String, value _: FieldValue) {}

    public func removeField(withKey _: String) {}

    public func createTemporaryDeviceCode(completion _: @escaping (Result<String, Error>) -> Void) {}

    public func startSpan(name: String, level: LogLevel, file: String? = nil, line: Int? = nil,
                          function: String? = nil, fields: Fields? = nil,
                          startTimeInterval: TimeInterval? = nil,
                          parentSpanID: UUID? = nil) -> Span
    {
        Span(
            logger: MockCoreLogging(),
            name: name,
            level: level,
            file: file,
            line: line,
            function: function,
            fields: fields,
            timeProvider: MockTimeProvider(),
            customStartTimeInterval: startTimeInterval,
            parentSpanID: parentSpanID
        )
    }

    public func setSleepMode(_ mode: Capture.SleepMode) {
        sleepMode = mode
    }
}
