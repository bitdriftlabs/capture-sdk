// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

private enum Keys {
    static let file = "_file"
    static let function = "_function"
    static let line = "_line"
}

/// Provides a logging interface. This interface separates the creation of a logger into two distinct
/// phases: initialization and activation.
protocol CoreLogging: AnyObject {
    /// Starts the logger. Needs to be called before logger is used to emit logs.
    func start()

    /// Logs messages using `normal` log type and non-blocking mode. Intended to be called from within the
    /// implementation of methods that expose logging interfaces to customers of the SDK.
    ///
    /// - parameter level:              The log level to use.
    /// - parameter message:            The message to log.
    /// - parameter file:               The unique file identifier that has the form module/file.
    /// - parameter line:               The line number on which the log is emitted.
    /// - parameter function:           The name of the declaration from within which the log is emitted.
    /// - parameter fields:             The fields to send as part of the log.
    /// - parameter matchingFields:     The matching fields to include as part of the log. These fields can be
    ///                                 read when processing a given log but are not a part of the log itself.
    /// - parameter error:              The error to log.
    /// - parameter type:               The type of the log message (i.e., `normal` or `internalsdk`).
    /// - parameter blocking:           Whether the call should block until the log is processed.
    /// - parameter occurredAtOverride: An override for the time the log occurred. If `nil`, current date is
    ///                                 used.
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        matchingFields: Fields?,
        error: Error?,
        type: Capture.Logger.LogType,
        blocking: Bool,
        occurredAtOverride: Date?
    )

    /// Writes a session replay screen log.
    ///
    /// - parameter screen:   The captured screen.
    /// - parameter duration: The duration of time the preparation of the log took.
    func logSessionReplayScreen(screen: SessionReplayCapture, duration: TimeInterval)

    /// Writes a session replay screen log.
    ///
    /// - parameter screen:   The captured screenshot. `nil` if screenshot couldn't be taken.
    /// - parameter duration: The duration of time the preparation of the log took.
    func logSessionReplayScreenshot(screen: SessionReplayCapture?, duration: TimeInterval)

    /// Writes a resource utilization log.
    ///
    /// - parameter fields:   The extra fields to include with the log.
    /// - parameter duration: The duration of time the preparation of the log took.
    func logResourceUtilization(fields: Fields, duration: TimeInterval)

    /// Writes an SDK configuration log.
    ///
    /// - parameter fields:   The extra fields to include with the log.
    /// - parameter duration: The duration of time the SDK configuration took.
    func logSDKStart(fields: Fields, duration: TimeInterval)

    /// Checks whether the app update log should be emitted.
    ///
    /// - parameter appVersion:  The version of the app.
    /// - parameter buildNumber: The app build number.
    ///
    /// - returns: Whether the app update log should be emitted or not.
    func shouldLogAppUpdate(
        appVersion: String,
        buildNumber: String
    ) -> Bool

    /// Writes an app update log.
    ///
    /// - parameter appVersion:   The version of the app.
    /// - parameter buildNumber:  The app build number.
    /// - parameter appSizeBytes: The size of the app installation. Expressed in bytes.
    /// - parameter duration:     The duration of time the preparation of the log took.
    func logAppUpdate(
        appVersion: String,
        buildNumber: String,
        appSizeBytes: UInt64,
        duration: TimeInterval
    )

    /// Writes an app launch TTI log event. This event should be logged only once per Logger configuration.
    /// Consecutive calls have no effect.
    ///
    /// - parameter duration: The time between a user's intent to launch the app and when the app becomes
    ///                       interactive.
    func logAppLaunchTTI(_ duration: TimeInterval)

    /// Logs a screen view event.
    ///
    /// - parameter screenName: The name of the screen.
    func logScreenView(screenName: String)

    /// Stars new session using configured session strategy.
    func startNewSession()

    /// Returns currently active session ID.
    ///
    /// - returns: Currently active session ID.
    func getSessionID() -> String

    /// Returns unique device ID.
    ///
    /// - returns: Unique device ID.
    func getDeviceID() -> String

    /// Adds a field to all logs emitted by the logger from this point forward.
    /// If a field with a given key has already been registered with the logger, its value is
    /// replaced with the new one.
    ///
    /// - parameter key:   The name of the field to add.
    /// - parameter value: The value of the field to add.
    func addField(withKey key: String, value: String)

    /// Removes a field with a given key. This operation has no effect if a field with the given key
    /// is not registered with the logger.
    ///
    /// - parameter key: The name of the field to remove.
    func removeField(withKey key: String)

    /// Flushes logger's state to disk.
    ///
    /// - parameter blocking: Whether the method should return only after the flushing completes.
    func flush(blocking: Bool)

    /// Sets a feature flag with an optional variant.
    ///
    /// - parameter flag:    The name of the flag to set
    /// - parameter variant: An optional variant to set the flag to
    func setFeatureFlag(withName flag: String, variant: String?)

    /// Sets multiple feature flags.
    ///
    /// - parameter flags: The flags to set
    func setFeatureFlags(_ flags: [FeatureFlag])

    /// Removes a feature flag.
    ///
    /// - parameter name: The name of the flag to remove
    func removeFeatureFlag(withName name: String)

    /// Clears all feature flags.
    ///
    func clearFeatureFlags()

    /// Retrieves the value of a given runtime variable.
    ///
    /// - parameter variable: The runtime variable.
    ///
    /// - returns: The runtime variable value.
    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T

    /// Handles a given error.
    ///
    /// - parameter context: Context information that  may be helpful when debugging a given error.
    /// - parameter error:   The error to report.
    func handleError(context: String, error: Error)

    /// Enables blocking shutdown operation. In practice, it makes the receiver's deinit wait for the complete
    /// shutdown of the underlying logger.
    ///
    /// For tests/profiling purposes only.
    func enableBlockingShutdown()

    /// Enables or disables sleep mode, which when active places the logger in minimal activity mode
    ///
    /// - parameter mode: The mode to use
    func setSleepMode(_ mode: SleepMode)

    /// Process pending crash reports
    ///
    /// - parameter reportProcessingSession: The report processing session type
    func processIssueReports(reportProcessingSession: ReportProcessingSession)
}

extension CoreLogging {
    /// Logs messages using `normal` log type and non-blocking mode. Intended to be called from within the
    /// implementation of methods that expose logging interfaces to customers of the SDK.
    ///
    /// - parameter level:              The log level to use.
    /// - parameter message:            The message to log.
    /// - parameter file:               The unique file identifier that has the form module/file.
    /// - parameter line:               The line number on which the log is emitted.
    /// - parameter function:           The name of the declaration from within which the log is emitted.
    /// - parameter fields:             The extra fields to send as part of the log.
    /// - parameter matchingFields:     These fields can be read when processing a given log but are not a
    ///                                 part
    ///                                 of the log itself.
    /// - parameter error:              The error to log.
    /// - parameter type:               The type of the log message (i.e., `normal` or `internalsdk`).
    /// - parameter blocking:           Whether the call should block until the log is processed.
    /// - parameter occurredAtOverride: An override for the time the log occurred. If `nil`, current date is
    ///                                 used.
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = nil,
        line: Int? = nil,
        function: String? = nil,
        fields: Fields? = nil,
        matchingFields: Fields? = nil,
        error: Error? = nil,
        type: Capture.Logger.LogType,
        blocking: Bool = false,
        occurredAtOverride: Date? = nil
    )
    {
        self.log(
            level: level,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            matchingFields: matchingFields,
            error: error,
            type: type,
            blocking: blocking,
            occurredAtOverride: occurredAtOverride
        )
    }

    /// Internal log function that allows for customizable types, indicating which OOTB log type it is, or if
    /// it is a normal log.
    ///
    /// - parameter level:    The log level to use.
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number on which the log is emitted.
    /// - parameter function: The name of the declaration from within which the log is emitted.
    func logInternal(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function
    )
    {
        self.log(
            level: level,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: nil,
            error: nil,
            type: .internalsdk,
            blocking: false
        )
    }
}
