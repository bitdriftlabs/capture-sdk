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

final class MockCoreLogging {
    struct Log {
        let level: LogLevel
        let message: String
        let file: String?
        let line: Int?
        let function: String?
        let fields: Fields?
        let matchingFields: Fields?
        let error: Error?
        let type: Logger.LogType
        let blocking: Bool
    }

    struct ResourceUtilizationLog {
        let fields: Fields
        let duration: TimeInterval
    }

    private(set) var logs = [Log]()
    var logExpectation: XCTestExpectation?

    private(set) var logAppUpdateCount = 0
    var logAppUpdateExpectation: XCTestExpectation?

    private(set) var resourceUtilizationLogs = [ResourceUtilizationLog]()
    var logResourceUtilizationExpectation: XCTestExpectation?

    var shouldLogAppUpdateEvent = false

    private(set) var mockedRuntimeVariables = [String: Any]()

    func mockRuntimeVariable<T: RuntimeValue>(_ variable: RuntimeVariable<T>, with value: T) {
        let values = [variable.name: value]
        self.mockedRuntimeVariables.mergeOverwritingConflictingKeys(values)
    }
}

extension MockCoreLogging: CoreLogging {
    static func makeLogger(
        apiKey _: String,
        bufferDirectory _: URL?,
        sessionStrategy _: SessionStrategy,
        metadataProvider _: Capture.MetadataProvider,
        resourceUtilizationTarget _: Capture.ResourceUtilizationTarget,
        appID _: String,
        releaseVersion _: String,
        network _: Network?,
        errorReporting _: RemoteErrorReporting,
        loggerBridgingFactoryProvider _: LoggerBridgingFactoryProvider
    ) -> CoreLogging {
        return MockCoreLogging()
    }

    func start() {}

    func startNewSession() {}

    func getSessionID() -> String { "foo" }

    func getDeviceID() -> String { "deviceID" }

    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        matchingFields: Fields?,
        error: Error?,
        type: Logger.LogType,
        blocking: Bool
    ) {
        self.logs.append(
            Log(
                level: level,
                message: message(),
                file: file,
                line: line,
                function: function,
                fields: fields,
                matchingFields: matchingFields,
                error: error,
                type: type,
                blocking: blocking
            )
        )
        self.logExpectation?.fulfill()
    }

    func logSessionReplay(screen _: SessionReplayScreenCapture, duration _: TimeInterval) {}

    func logResourceUtilization(fields: Fields, duration: TimeInterval) {
        self.resourceUtilizationLogs.append(ResourceUtilizationLog(fields: fields, duration: duration))
        self.logResourceUtilizationExpectation?.fulfill()
    }

    func logSDKStart(fields _: Fields, duration _: TimeInterval) {}

    func shouldLogAppUpdate(appVersion _: String, buildNumber _: String) -> Bool {
        return self.shouldLogAppUpdateEvent
    }

    func logAppUpdate(
        appVersion _: String,
        buildNumber _: String,
        appSizeBytes _: UInt64,
        duration _: TimeInterval
    ) {
        self.logAppUpdateCount += 1
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

    func handleError(context _: String, error _: Error) {}

    func enableBlockingShutdown() {}
}
