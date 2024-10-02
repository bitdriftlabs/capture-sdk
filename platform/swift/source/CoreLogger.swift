// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation

/// A layer of abstraction used as a thin wrapper around Rust logger bridge. It introduces easy-to-use
/// logging methods.
final class CoreLogger {
    private let underlyingLogger: LoggerBridging

    /// Initializes a new instance of the logger using provided Rust Logger bridging logger creation closure.
    /// It breaks-down Rust logger initialization process into two separate stages:
    /// initialization and the actual start of the logger. This separation helps us to avoid data race
    /// condition in the Swift layer.
    ///
    /// - parameter logger: The logger.
    init(logger: LoggerBridging) {
        self.underlyingLogger = logger
    }

    func start() {
        self.underlyingLogger.start()
    }
}

extension CoreLogger: CoreLogging {
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = nil,
        line: Int? = nil,
        function: String? = nil,
        fields: Fields? = nil,
        matchingFields: Fields? = nil,
        error: Error? = nil,
        type: Logger.LogType,
        blocking: Bool = false
    )
    {
        if type == .internalsdk && !self.runtimeValue(.internalLogs) {
            return
        }

        var fields = fields ?? [:]

        if let error {
            fields.mergeOverwritingConflictingKeys(error.toFields())
        }

        if let line {
            fields["_line"] = String(line)
        }

        if let file {
            fields["_file"] = file
        }

        if let function {
            fields["_function"] = function
        }

        let fieldsOrNil: InternalFields? = if fields.isEmpty {
            nil
        } else {
            self.convertFields(fields: fields)
        }

        self.underlyingLogger.log(
            level: level,
            message: message(),
            fields: fieldsOrNil,
            matchingFields: matchingFields.flatMap(self.convertFields),
            type: type,
            blocking: blocking
        )
    }

    func logSessionReplay(screen: SessionReplayScreenCapture, duration: TimeInterval) {
        self.underlyingLogger.logSessionReplay(
            fields: self.convertFields(fields: ["screen": screen]),
            duration: duration
        )
    }

    func logResourceUtilization(fields: Fields, duration: TimeInterval) {
        self.underlyingLogger.logResourceUtilization(
            fields: self.convertFields(fields: fields),
            duration: duration
        )
    }

    func logSDKStarted(fields: Fields, duration: TimeInterval) {
        self.underlyingLogger.logSDKStarted(
            fields: self.convertFields(fields: fields),
            duration: duration
        )
    }

    func shouldLogAppUpdate(appVersion: String, buildNumber: String) -> Bool {
        return self.underlyingLogger.shouldLogAppUpdate(appVersion: appVersion, buildNumber: buildNumber)
    }

    func logAppUpdate(
        appVersion: String,
        buildNumber: String,
        appSizeBytes: UInt64,
        duration: TimeInterval
    )
    {
        self.underlyingLogger.logAppUpdate(
            appVersion: appVersion,
            buildNumber: buildNumber,
            appSizeBytes: appSizeBytes,
            duration: duration
        )
    }

    func logAppLaunchTTI(_ duration: TimeInterval) {
        self.underlyingLogger.logAppLaunchTTI(duration)
    }

    func startNewSession() {
        self.underlyingLogger.startNewSession()
    }

    func getSessionID() -> String {
        self.underlyingLogger.getSessionID()
    }

    func getDeviceID() -> String {
        self.underlyingLogger.getDeviceID()
    }

    func addField(withKey key: String, value: String) {
        self.underlyingLogger.addField(withKey: key, value: value)
    }

    func removeField(withKey key: String) {
        self.underlyingLogger.removeField(withKey: key)
    }

    func flush(blocking: Bool) {
        self.underlyingLogger.flush(blocking: blocking)
    }

    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        self.underlyingLogger.runtimeValue(variable)
    }

    func handleError(context: String, error: Error) {
        self.underlyingLogger.handleError(context: context, error: error)
    }

    func enableBlockingShutdown() {
        self.underlyingLogger.enableBlockingShutdown()
    }

    private func convertFields(fields: Fields) -> [CapturePassable.Field] {
        fields.compactMap { fieldKeyValue in
            do {
                return try Field.make(keyValue: fieldKeyValue)
            } catch let error {
                self.handleError(
                    context: "write_log: failed to encode field",
                    error: FieldEncodingError(key: fieldKeyValue.key, error: error)
                )
                return nil
            }
        }
    }
}

private struct FieldEncodingError: Error {
    let key: String
    let error: Error
}

extension FieldEncodingError: CustomStringConvertible {
    var description: String {
        // swiftlint:disable:next line_length
        return "failed to encode field with \"\(self.key)\" key: \(self.error.localizedDescription) \(String(describing: self.error))"
    }
}
