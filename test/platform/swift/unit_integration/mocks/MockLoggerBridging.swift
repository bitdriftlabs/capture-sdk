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

final class MockLoggerBridging {
    struct HandledError {
        let context: String
        let error: Error
    }

    struct Log {
        let level: LogLevel
        let message: String
        let fields: InternalFields?
        let matchingFields: InternalFields?
        let type: Logger.LogType
        let blocking: Bool
    }

    private(set) var mockedRuntimeVariables = [String: Any]()

    private(set) var underlyingLogs = Atomic([Log]())
    var logs: [Log] {
        return self.underlyingLogs.load()
    }

    private(set) var errors: [HandledError] = []

    var shouldLogAppUpdateEvent = false

    var logAppUpdateExpectation: XCTestExpectation?

    func mockRuntimeVariable<T: RuntimeValue>(_ variable: RuntimeVariable<T>, with value: T) {
        let values = [variable.name: value]
        self.mockedRuntimeVariables.mergeOverwritingConflictingKeys(values)
    }
}

extension MockLoggerBridging: LoggerBridging {
    func start() {}

    func getSessionID() -> String { "foo" }

    func startNewSession() {}

    func getDeviceID() -> String { "deviceID" }

    func log(
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

    func logSessionReplay(fields _: [Field], duration _: TimeInterval) {}

    func logResourceUtilization(fields _: [Field], duration _: TimeInterval) {}

    func logSDKStart(fields _: [Field], duration _: TimeInterval) {}

    func shouldLogAppUpdate(appVersion _: String, buildNumber _: String) -> Bool {
        self.shouldLogAppUpdateEvent
    }

    func logAppUpdate(
        appVersion _: String,
        buildNumber _: String,
        appSizeBytes _: UInt64,
        duration _: TimeInterval
    ) {
        self.logAppUpdateExpectation?.fulfill()
    }

    func logAppLaunchTTI(_: TimeInterval) {}

    func addField(withKey _: String, value _: String) {}

    func removeField(withKey _: String) {}

    func flush(blocking _: Bool) {}

    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        if let value = self.mockedRuntimeVariables[variable.name] {
            // swiftlint:disable:next force_cast
            value as! T
        } else {
            variable.defaultValue
        }
    }

    func handleError(context: String, error: Error) {
        self.errors.append(HandledError(context: context, error: error))
    }

    func enableBlockingShutdown() {}
}
