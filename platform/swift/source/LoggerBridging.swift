// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
internal import CapturePassable
import Foundation

typealias InternalFields = [CapturePassable.Field]

/// An abstraction around Rust logger calls. It's main purpose is to help with dependency injection so that
/// tests do not call into Rust methods.
protocol LoggerBridging {
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        fields: InternalFields?,
        matchingFields: InternalFields?,
        type: Capture.Logger.LogType,
        blocking: Bool,
        occurredAtOverride: Date?
    )

    func logSessionReplayScreen(fields: InternalFields, duration: TimeInterval)

    func logSessionReplayScreenshot(fields: InternalFields, duration: TimeInterval)

    func logResourceUtilization(fields: InternalFields, duration: TimeInterval)

    func logSDKStart(fields: [CapturePassable.Field], duration: TimeInterval)

    func shouldLogAppUpdate(
        appVersion: String,
        buildNumber: String
    ) -> Bool

    func logAppUpdate(
        appVersion: String,
        buildNumber: String,
        appSizeBytes: UInt64,
        duration: TimeInterval
    )

    func logAppLaunchTTI(_ duration: TimeInterval)

    func logScreenView(screenName: String)

    func start()

    func startNewSession()

    func getSessionID() -> String

    func getDeviceID() -> String

    func addField(withKey key: String, value: String)

    func removeField(withKey key: String)

    /// Flushes logger's state to disk.
    ///
    /// - parameter blocking: Whether the method should return only after the flushing completes.
    func flush(blocking: Bool)

    /// Sets a feature flag with an optional variant.
    ///
    /// - parameter name:    The name of the flag to set
    /// - parameter variant: An optional variant to set the flag to
    func setFeatureFlag(withName name: String, variant: String?)

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

    /// Retrieves a given runtime variable.
    ///
    /// - parameter feature: The runtime variable.
    ///
    /// - returns: The value of a runtime variable.
    func runtimeValue<T>(_ feature: RuntimeVariable<T>) -> T

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

    /// Enables or disables sleep mode
    ///
    /// - parameter mode: the mode to use
    func setSleepMode(_ mode: SleepMode)

    /// Process pending crash reports
    func processCrashReports()
}
