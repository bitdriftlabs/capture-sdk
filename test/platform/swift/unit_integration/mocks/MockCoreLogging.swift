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
    public struct OotbFieldUpdate {
        public let key: String
        public let value: String
    }

    public struct Log {
        public let level: LogLevel
        public let message: String
        public let file: String?
        public let line: Int?
        public let function: String?
        public let fields: Fields?
        public let matchingFields: Fields?
        public let error: Error?
        public let type: Capture.Logger.LogType
        public let blockingBehavior: LogBlockingBehavior
        public let occurredAtOverride: Date?
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

    public private(set) var flushCalls = [Bool]()
    public var flushExpectation: XCTestExpectation?

    public var shouldLogAppUpdateEvent = false

    public private(set) var mockedRuntimeVariables = [String: Any]()

    public private(set) var didNotifyMemoryPressure = false
    public private(set) var notifyMemoryPressureValue: MemoryPressureLevel?

    public var mockedPreviousMemoryPressureLevel: MemoryPressureLevel?

    public private(set) var setEntityIDs = [String]()

    public private(set) var clearEntityIDCallCount = 0
    private let ootbFieldsLock = NSLock()
    private var storedOotbFields = [String: String]()
    private var storedOotbFieldUpdates = [OotbFieldUpdate]()

    public var ootbFields: [String: String] {
        self.withOotbFieldsLock { self.storedOotbFields }
    }

    public var ootbFieldUpdateCount: Int {
        self.withOotbFieldsLock { self.storedOotbFieldUpdates.count }
    }

    public var ootbFieldUpdates: [OotbFieldUpdate] {
        self.withOotbFieldsLock { self.storedOotbFieldUpdates }
    }

    public init() {}

    public func mockRuntimeVariable<T: RuntimeValue>(_ variable: RuntimeVariable<T>, with value: T) {
        let values = [variable.name: value]
        self.mockedRuntimeVariables.mergeOverwritingConflictingKeys(values)
    }

    private func withOotbFieldsLock<T>(_ body: () -> T) -> T {
        self.ootbFieldsLock.lock()
        defer { self.ootbFieldsLock.unlock() }
        return body()
    }
}

extension MockCoreLogging: CoreLogging {
    public func start() {}

    public func startNewSession(sessionID _: String?) {}

    public func getSessionID() -> String { "foo" }

    public func getDeviceID() -> String { "deviceID" }

    public func getSdkStatus() -> SdkStatus {
        SdkStatus(initializationState: .notStarted, lastHandshakeTime: nil, lastConfigDeliveryTime: nil)
    }

    public func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        matchingFields: Fields?,
        error: Error?,
        type: Capture.Logger.LogType,
        blockingBehavior: LogBlockingBehavior,
        occurredAtOverride: Date?
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
                blockingBehavior: blockingBehavior,
                occurredAtOverride: occurredAtOverride
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

    public func logScreenView(screenName _: String) {}

    public func addField(withKey _: String, value _: String) {}

    public func updateOotbField(withKey key: String, value: String) {
        self.withOotbFieldsLock {
            self.storedOotbFieldUpdates.append(OotbFieldUpdate(key: key, value: value))
            self.storedOotbFields[key] = value
        }
    }

    public func removeField(withKey _: String) {}

    public func flush(blocking: Bool) {
        self.flushCalls.append(blocking)
        self.flushExpectation?.fulfill()
    }

    public func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        if let value = self.mockedRuntimeVariables[variable.name] {
            // swiftlint:disable:next force_cast
            value as! T
        } else {
            variable.defaultValue
        }
    }

    public func notifyMemoryPressure(level: MemoryPressureLevel) {
        didNotifyMemoryPressure = true
        notifyMemoryPressureValue = level
    }

    public func previousMemoryPressureLevel() -> MemoryPressureLevel {
        return mockedPreviousMemoryPressureLevel ?? .unknown
    }

    public func handleError(context _: String, error _: Error) {}

    public func enableBlockingShutdown() {}

    public func setSleepMode(_ mode: SleepMode) {}

    public func processIssueReports(reportProcessingSession: ReportProcessingSession) {}

    public func setFeatureFlagExposure(withName flag: String, variant: String) {}

    public func setEntityID(_ entityID: String) {
        self.setEntityIDs.append(entityID)
    }

    public func clearEntityID() {
        self.clearEntityIDCallCount += 1
    }
}
