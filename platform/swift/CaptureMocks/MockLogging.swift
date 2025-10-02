import Capture
import XCTest

public struct Log {
    public let level: LogLevel
    public let message: String
    public let fields: Fields?
    public let error: Error?
}

public final class MockLogging: Logging {
    public var logExpectation: XCTestExpectation?
    public private(set) var logs = [Log]()

    public init() {}

    public func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file _: String?,
        line _: Int?,
        function _: String?,
        fields: Fields?,
        error: Error?
    ) {
        self.logs.append(
            Log(
                level: level,
                message: message(),
                fields: fields,
                error: error
            )
        )
        self.logExpectation?.fulfill()
    }

    public let sessionID = "foo"

    public let sessionURL = "fooURL"

    public let deviceID = "deviceID"

    public func startNewSession() {}

    public func addField(withKey _: String, value _: Encodable & Sendable) {}

    public func removeField(withKey _: String) {}

    public func createTemporaryDeviceCode(completion _: @escaping (Result<String, Error>) -> Void) {}

    public func startSpan(
        name _: String,
        level _: LogLevel,
        file _: String?,
        line _: Int?,
        function _: String?,
        fields _: Fields?,
        startTimeInterval _: TimeInterval?,
        parentSpanID _: UUID?
    ) -> Span {
        fatalError("TODO: needs to be implemented")
    }

    public func logAppLaunchTTI(_: TimeInterval) {}

    public func logScreenView(screenName _: String) {}

    public func setSleepMode(_: Capture.SleepMode) {}
}
