// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

/// Logging interface mock.
final class MockLogging {
    struct Log {
        enum Object {
            case message(String)
            case request(HTTPRequestInfo)
            case response(HTTPResponseInfo)
        }

        let level: LogLevel
        let object: Object
        let fields: Fields?

        func message() -> String? {
            if case let .message(message) = self.object {
                return message
            }

            return nil
        }

        func request() -> HTTPRequestInfo? {
            if case let .request(request) = self.object {
                return request
            }

            return nil
        }

        func response() -> HTTPResponseInfo? {
            if case let .response(response) = self.object {
                return response
            }

            return nil
        }
    }

    var logExpectation: XCTestExpectation?
    var logRequestExpectation: XCTestExpectation?
    var logResponseExpectation: XCTestExpectation?

    /// The logs emitted by the logger
    private(set) var logs = [Log]()
    /// The number of logs emitted by the logger.
    var logsCount: Int { self.logs.count }
    /// A closure that's called every time a log is emitted by the logger.
    var onLog: (_ log: Log) -> Void = { _ in }
}

extension MockLogging: Logging {
    var sessionID: String { "fooID" }
    var sessionURL: String { "fooURL" }

    func startNewSession() {}

    var deviceID: String { "deviceID" }

    func log(
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

    func log(_ request: HTTPRequestInfo, file _: String?, line _: Int?, function _: String?) {
        let log = Log(
            level: .debug,
            object: .request(request),
            fields: request.toFields()
        )

        self.logs.append(log)
        self.logRequestExpectation?.fulfill()
        self.onLog(log)
    }

    func log(_ response: HTTPResponseInfo, file _: String?, line _: Int?, function _: String?) {
        let log = Log(
            level: .debug,
            object: .response(response),
            fields: response.toFields()
        )

        self.logs.append(log)
        self.logResponseExpectation?.fulfill()
        self.onLog(log)
    }

    func logAppLaunchTTI(_: TimeInterval) {}

    func addField(withKey _: String, value _: FieldValue) {}

    func removeField(withKey _: String) {}

    func createTemporaryDeviceCode(completion _: @escaping (Result<String, Error>) -> Void) {}
}
