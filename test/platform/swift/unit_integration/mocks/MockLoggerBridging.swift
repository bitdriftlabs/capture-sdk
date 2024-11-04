// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import Foundation
import XCTest

public final class MockLoggerBridging {
    public struct HandledError {
        public let context: String
        public let error: Error
    }

    public struct Log {
        public let level: LogLevel
        public let message: String
        public let fields: InternalFields?
        public let matchingFields: InternalFields?
        public let type: Logger.LogType
        public let blocking: Bool
    }

    public private(set) var mockedRuntimeVariables = [String: Any]()

    public private(set) var underlyingLogs = Atomic([Log]())
    public var logs: [Log] {
        return self.underlyingLogs.load()
    }

    public private(set) var errors: [HandledError] = []

    public var shouldLogAppUpdateEvent = false

    public var logAppUpdateExpectation: XCTestExpectation?

    public init() {}

    public func mockRuntimeVariable<T: RuntimeValue>(_ variable: RuntimeVariable<T>, with value: T) {
        let values = [variable.name: value]
        self.mockedRuntimeVariables.mergeOverwritingConflictingKeys(values)
    }
}

extension MockLoggerBridging: LoggerBridging {
    public func start() {}

    public func getSessionID() -> String { "foo" }

    public func startNewSession() {}

    public func getDeviceID() -> String { "deviceID" }

    public func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        fields: InternalFields?,
        matchingFields: InternalFields?,
        type: Logger.LogType,
        blocking: Bool
    ) {
        self.underlyingLogs.update {
            $0.append(
                Log(
                    level: level,
                    message: message(),
                    fields: fields,
                    matchingFields: matchingFields,
                    type: type,
                    blocking: blocking
                )
            )
        }
    }

    public func logSessionReplayScreen(fields _: [Field], duration _: TimeInterval) {}

    public func logSessionReplayScreenshot(fields _: [Field], duration _: TimeInterval) {}

    public func logResourceUtilization(fields _: [Field], duration _: TimeInterval) {}

    public func logSDKStart(fields _: [Field], duration _: TimeInterval) {}

    public func shouldLogAppUpdate(appVersion _: String, buildNumber _: String) -> Bool {
        self.shouldLogAppUpdateEvent
    }

    public func logAppUpdate(
        appVersion _: String,
        buildNumber _: String,
        appSizeBytes _: UInt64,
        duration _: TimeInterval
    ) {
        self.logAppUpdateExpectation?.fulfill()
    }

    public func logAppLaunchTTI(_: TimeInterval) {}

    public func addField(withKey _: String, value _: String) {}

    public func removeField(withKey _: String) {}

    public func flush(blocking _: Bool) {}

    public func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        if let value = self.mockedRuntimeVariables[variable.name] {
            // swiftlint:disable:next force_cast
            value as! T
        } else {
            variable.defaultValue
        }
    }

    public func handleError(context: String, error: Error) {
        self.errors.append(HandledError(context: context, error: error))
    }

    public func enableBlockingShutdown() {}
}
