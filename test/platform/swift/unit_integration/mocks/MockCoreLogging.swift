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

public final class MockCoreLogging {
    public struct Log {
        public let level: LogLevel
        public let message: String
        public let file: String?
        public let line: Int?
        public let function: String?
        public let fields: Fields?
        public let matchingFields: Fields?
        public let error: Error?
        public let type: Logger.LogType
        public let blocking: Bool
    }

    public struct ResourceUtilizationLog {
        public let fields: Fields
        public let duration: TimeInterval
    }

    public struct SessionReplayScreenLog {
        public let screen: SessionReplayCapture
        public let duration: TimeInterval
    }

    public private(set) var logs = [Log]()
    public var logExpectation: XCTestExpectation?

    public private(set) var logAppUpdateCount = 0
    public var logAppUpdateExpectation: XCTestExpectation?

    public private(set) var resourceUtilizationLogs = [ResourceUtilizationLog]()
    public var logResourceUtilizationExpectation: XCTestExpectation?

    public private(set) var sessionReplayScreenLogs = [SessionReplayScreenLog]()
    public var logSessionReplayScreenExpectation: XCTestExpectation?

    public var shouldLogAppUpdateEvent = false

    public private(set) var mockedRuntimeVariables = [String: Any]()

    public init() {}

    public func mockRuntimeVariable<T: RuntimeValue>(_ variable: RuntimeVariable<T>, with value: T) {
        let values = [variable.name: value]
        self.mockedRuntimeVariables.mergeOverwritingConflictingKeys(values)
    }
}

extension MockCoreLogging: CoreLogging {
    public static func makeLogger(
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

    public func start() {}

    public func startNewSession() {}

    public func getSessionID() -> String { "foo" }

    public func getDeviceID() -> String { "deviceID" }

    public func log(
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

    public func logSessionReplayScreen(screen: SessionReplayCapture, duration: TimeInterval) {
        self.sessionReplayScreenLogs.append(SessionReplayScreenLog(
                                                screen: screen, duration: duration)
        )
        self.logSessionReplayScreenExpectation?.fulfill()
    }

    public func logSessionReplayScreenshot(screen _: SessionReplayCapture?, duration _: TimeInterval) {}

    public func logResourceUtilization(fields: Fields, duration: TimeInterval) {
        self.resourceUtilizationLogs.append(ResourceUtilizationLog(fields: fields, duration: duration))
        self.logResourceUtilizationExpectation?.fulfill()
    }

    public func logSDKStart(fields _: Fields, duration _: TimeInterval) {}

    public func shouldLogAppUpdate(appVersion _: String, buildNumber _: String) -> Bool {
        return self.shouldLogAppUpdateEvent
    }

    public func logAppUpdate(
        appVersion _: String,
        buildNumber _: String,
        appSizeBytes _: UInt64,
        duration _: TimeInterval
    ) {
        self.logAppUpdateCount += 1
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

    public func handleError(context _: String, error _: Error) {}

    public func enableBlockingShutdown() {}
}
