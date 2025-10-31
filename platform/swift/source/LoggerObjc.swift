// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// A configuration representing the feature set enabled for Capture via Objective-C.
@objc
public final class CAPConfiguration: NSObject {
    let underlyingConfig: Configuration
    public let enableURLSessionIntegration: Bool

    /// Initializes a new instance of the Capture configuration.
    ///
    /// - parameter enableFatalIssueReporting:   true if Capture should enable Fatal Issue Reporting
    /// - parameter enableURLSessionIntegration: true if Capture should enable Fatal Issue Reporting
    @objc
    public init(enableFatalIssueReporting: Bool, enableURLSessionIntegration: Bool) {
        self.underlyingConfig = Configuration(enableFatalIssueReporting: enableFatalIssueReporting)
        self.enableURLSessionIntegration = enableURLSessionIntegration
    }

    /// Initializes a new instance of the Capture configuration.
    ///
    /// - parameter enableFatalIssueReporting:   true if Capture should enable Fatal Issue Reporting
    /// - parameter enableURLSessionIntegration: true if Capture should enable Fatal Issue Reporting
    /// - parameter sleepMode:                   CAPSleepModeActive if Capture should initialize in minimal activity mode
    @objc
    public init(enableFatalIssueReporting: Bool, enableURLSessionIntegration: Bool, sleepMode: SleepMode) {
        self.underlyingConfig = Configuration(sleepMode: sleepMode, enableFatalIssueReporting: enableFatalIssueReporting)
        self.enableURLSessionIntegration = enableURLSessionIntegration
    }
}

@objc
public final class FeatureFlag: NSObject {
    @objc
    let name: String
    @objc
    let variant: String?

    @objc
    public init(name: String, variant: String?) {
        self.name = name
        self.variant = variant
    }
}

// Make this class not available to Swift code. It should be used by Objective-c code only.
@available(swift, obsoleted: 1.0)
@objc(CAPLogger)
public final class LoggerObjc: NSObject {
    @available(*, unavailable)
    override public init() {
        fatalError("init() is not available. Use static methods instead.")
    }

    /// Initializes the Capture SDK with the specified API key and session strategy.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:          The API key provided by bitdrift.
    /// - parameter sessionStrategy: A session strategy for the management of session IDs.
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc
    ) {
        Capture.Logger
            .start(
                withAPIKey: apiKey,
                sessionStrategy: sessionStrategy.underlyingSessionStrategy,
                // swiftlint:disable:next force_unwrapping use_static_string_url_init
                apiURL: URL(string: "https://api.bitdrift.io")!
            )
    }

    /// Initializes the Capture SDK with the specified API key and session strategy.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:          The API key provided by bitdrift.
    /// - parameter sessionStrategy: A session strategy for the management of session IDs.
    /// - parameter configuration:   Additional options for the Capture Logger
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc,
        configuration: CAPConfiguration
    ) {
        let logger = Capture.Logger
            .start(
                withAPIKey: apiKey,
                sessionStrategy: sessionStrategy.underlyingSessionStrategy,
                configuration: configuration.underlyingConfig,
                // swiftlint:disable:next force_unwrapping use_static_string_url_init
                apiURL: URL(string: "https://api.bitdrift.io")!
            )

        if let logger, configuration.enableURLSessionIntegration {
            logger.enableIntegrations([.urlSession()], disableSwizzling: false)
        }
    }

    /// Initializes the Capture SDK with the specified API key and session strategy.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:          The API key provided by bitdrift.
    /// - parameter sessionStrategy: A session strategy for the management of session IDs.
    /// - parameter configuration:   Additional options for the Capture Logger
    /// - parameter apiURL:          The base URL of Capture API.
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc,
        configuration: CAPConfiguration,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!
    ) {
        let logger = Capture.Logger
            .start(
                withAPIKey: apiKey,
                sessionStrategy: sessionStrategy.underlyingSessionStrategy,
                configuration: configuration.underlyingConfig,
                apiURL: apiURL
            )

        if let logger, configuration.enableURLSessionIntegration {
            logger.enableIntegrations([.urlSession()], disableSwizzling: false)
        }
    }

    /// Initializes the Capture SDK with the specified API key and session strategy.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:                      The API key provided by bitdrift.
    /// - parameter sessionStrategy:             A session strategy for the management of session IDs.
    /// - parameter apiURL:                      The base URL of the Capture API. Rely on its default value
    ///                                          unless
    ///                                          specifically instructed otherwise during discussions with
    ///                                          Bitdrift.
    ///                                          Defaults to Bitdrift's hosted Compose API base URL.
    /// - parameter enableURLSessionIntegration: A flag indicating if automatic URLSession capture is enabled.
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!,
        enableURLSessionIntegration: Bool = true
    ) {
        let logger = Capture.Logger
            .start(
                withAPIKey: apiKey,
                sessionStrategy: sessionStrategy.underlyingSessionStrategy,
                apiURL: apiURL
            )

        if let logger, enableURLSessionIntegration {
            logger.enableIntegrations([.urlSession()], disableSwizzling: false)
        }
    }

    /// Initializes the Capture SDK with the specified API key and session strategy.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:                      The API key provided by bitdrift.
    /// - parameter sessionStrategy:             A session strategy for the management of session IDs.
    /// - parameter apiURL:                      The base URL of the Capture API. Rely on its default value
    ///                                          unless
    ///                                          specifically instructed otherwise during discussions with
    ///                                          Bitdrift.
    ///                                          Defaults to Bitdrift's hosted Compose API base URL.
    /// - parameter enableURLSessionIntegration: A flag indicating if automatic URLSession capture is enabled.
    /// - parameter sleepMode:                   .active if Capture should be initialized in minimal activity mode.
    /// - parameter enableFatalIssueReporting:   true if Capture should enable Fatal Issue Reporting.
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!,
        enableURLSessionIntegration: Bool = true,
        sleepMode: SleepMode = .disabled,
        enableFatalIssueReporting: Bool = true
    ) {
        let logger = Capture.Logger
            .start(
                withAPIKey: apiKey,
                sessionStrategy: sessionStrategy.underlyingSessionStrategy,
                configuration: Configuration(sleepMode: sleepMode, enableFatalIssueReporting: enableFatalIssueReporting),
                apiURL: apiURL
            )

        if let logger, enableURLSessionIntegration {
            logger.enableIntegrations([.urlSession()], disableSwizzling: false)
        }
    }

    /// Sets the operation mode of the logger, where activating sleep mode
    /// reduces activity to a minimal level
    ///
    /// - parameter mode: the mode to use
    @objc
    public static func setSleepMode(_ mode: SleepMode) {
        Capture.Logger.setSleepMode(mode)
    }

    /// Get the current version of the Capture library
    ///
    /// - returns: the version as a String
    @objc
    public static func sdkVersion() -> String {
        return capture_get_sdk_version()
    }

    /// Defines the initialization of a new session within the current configured logger.
    /// If no logger is configured, this is a no-op.
    @objc
    public static func startNewSession() {
        Capture.Logger.startNewSession()
    }

    /// Logs a trace level message to the default logger instance.
    ///
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func logTrace(_ message: String, fields: [String: String]?) {
        // TODO(Augustyniak): Support passing of `file`, `line`, and potentially `function` arguments to
        // this function. It would require us to have macros that folks could use to log instead of calling
        // Logger's methods directly.
        Capture.Logger.logTrace(
            message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Logs a debug level message to the default logger instance.
    ///
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func logDebug(_ message: String, fields: [String: String]?) {
        // TODO(Augustyniak): Support passing of `file`, `line`, and potentially `function` arguments to
        // this function. It would require us to have macros that folks could use to log instead of calling
        // Logger's methods directly.
        Capture.Logger.logDebug(
            message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Logs an info level message to the default logger instance.
    ///
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func logInfo(_ message: String, fields: [String: String]?) {
        // TODO(Augustyniak): Support passing of `file`, `line`, and potentially `function` arguments to
        // this function. It would require us to have macros that folks could use to log instead of calling
        // Logger's methods directly.
        Capture.Logger.logInfo(
            message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Logs a warning level message to the default logger instance.
    ///
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func logWarning(_ message: String, fields: [String: String]?) {
        // TODO(Augustyniak): Support passing of `file`, `line`, and potentially `function` arguments to
        // this function. It would require us to have macros that folks could use to log instead of calling
        // Logger's methods directly.
        Capture.Logger.logInfo(
            message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Logs an error level message to the default logger instance.
    ///
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func logError(_ message: String, fields: [String: String]?) {
        // TODO(Augustyniak): Support passing of `file`, `line`, and potentially `function` arguments to
        // this function. It would require us to have macros that folks could use to log instead of calling
        // Logger's methods directly.
        Capture.Logger.logInfo(
            message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Logs a message at a specified level to the default logger instance.
    ///
    /// - parameter level:   The severity of the log.
    /// - parameter message: The message to log.
    /// - parameter fields:  The extra fields to send as part of the log.
    @objc
    public static func log(level: LogLevel, message: String, fields: [String: String]?) {
        Capture.Logger.log(
            level: level,
            message: message,
            file: nil,
            line: nil,
            function: nil,
            fields: fields
        )
    }

    /// Writes an app launch TTI log event. This event should be logged only once per Logger start.
    /// Consecutive calls have no effect.
    ///
    /// - parameter duration: The time between a user's intent to launch the app and when the app becomes
    ///                       interactive. Calls with a negative duration are ignored.
    @objc
    public static func logAppLaunchTTI(_ duration: TimeInterval) {
        Capture.Logger.logAppLaunchTTI(duration)
    }

    /// Writes a log that indicates that a "screen" has been presented. This is useful for snakeys and other
    /// journeys visualization.
    ///
    /// - parameter screenName: The human readable unique identifier of the screen being presented.
    @objc
    public static func logScreenView(screenName: String) {
        Capture.Logger.logScreenView(screenName: screenName)
    }

    /// Retrieves the session ID. It's equal to `nil` prior to the configuration of Capture SDK.
    ///
    /// - returns: The ID for the current ongoing session.
    @objc
    public static func sessionID() -> String? {
        return Capture.Logger.sessionID
    }

    /// Retrieves the session URL. It is `nil` before the Capture SDK is started.
    ///
    /// - returns: The Session URL represented as a string.
    @objc
    public static func sessionURL() -> String? {
        return Capture.Logger.sessionURL
    }

    /// A canonical identifier for a device that remains consistent as long as an application
    /// is not reinstalled.
    ///
    /// The value of this property is different for apps from the same vendor running on
    /// the same device. It is equal to `nil` prior to the configuration of bitdrift Capture SDK.
    ///
    /// - returns: The device ID.
    @objc
    public static func deviceID() -> String? {
        return Capture.Logger.deviceID
    }

    // MARK: - Extra

    /// Adds a field to all logs emitted by the logger from this point forward.
    /// If a field with a given key has already been registered with the logger, its value is
    /// replaced with the new one.
    ///
    /// Fields added with this method take precedence over fields provided by registered `FieldProvider`s
    /// and are overwritten by fields provided with custom logs.
    ///
    /// - parameter key:   The name of the field to add.
    /// - parameter value: The value of the field to add.
    @objc
    public static func addField(key: String, value: String) {
        Capture.Logger.addField(withKey: key, value: value)
    }

    /// Removes a field with a given key. This operation has no effect if a field with the given key
    /// is not registered with the logger.
    ///
    /// - parameter key: The name of the field to remove.
    @objc
    public static func removeField(key: String) {
        Capture.Logger.removeField(withKey: key)
    }

    /// Sets a feature flag with an optional variant.
    ///
    /// - parameter name:    The name of the flag to set
    /// - parameter variant: An optional variant to set the flag to
    @objc
    public static func setFeatureFlag(withName name: String, variant: String?) {
        Capture.Logger.setFeatureFlag(withName: name, variant: variant)
    }

    /// Sets multiple feature flags.
    ///
    /// - parameter flags: The flags to set
    @objc
    public static func setFeatureFlags(_ flags: [FeatureFlag]) {
        Capture.Logger.setFeatureFlags(flags)
    }

    /// Removes a feature flag.
    ///
    /// - parameter name: The name of the flag to remove
    @objc
    public static func removeFeatureFlag(withName name: String) {
        Capture.Logger.removeFeatureFlag(withName: name)
    }

    /// Clears all feature flags.
    ///
    @objc
    public static func clearFeatureFlags() {
        Capture.Logger.clearFeatureFlags()
    }

    /// Creates a temporary device code that can be fed into other bitdrift tools to stream logs from a
    /// given device in real-time fashion. The creation of the device code requires communication with
    /// the bitdrift remote service.
    ///
    /// - parameter completion: The closure that is called when the operation is complete. Called on the
    ///                         main queue.
    @objc
    public static func createTemporaryDeviceCode(completion: @escaping (String?, Error?) -> Void) {
        Capture.Logger.createTemporaryDeviceCode { result in
            switch result {
            case .success(let deviceCode):
                completion(deviceCode, nil)
            case .failure(let error):
                completion(nil, error)
            }
        }
    }
}

/// Describes the strategy to use for session management.
@objc(CAPSessionStrategy)
public final class SessionStrategyObjc: NSObject {
    fileprivate let underlyingSessionStrategy: SessionStrategy

    @available(*, unavailable)
    override public init() {
        fatalError("init() is not available. Use static methods instead.")
    }

    init(sessionStrategy: SessionStrategy) {
        self.underlyingSessionStrategy = sessionStrategy
    }

    /// A session strategy that never expires the session ID but does not survive process restart.
    ///
    /// Whenever a new session is manually started via `startNewSession` method call, a new random session ID
    /// is generated.
    ///
    /// - returns: The fixed session strategy.
    @objc
    public static func fixed() -> SessionStrategyObjc {
        return SessionStrategyObjc(sessionStrategy: .fixed())
    }

    /// A session strategy that never expires the session ID but does not survive process restart.
    ///
    /// The initial session ID is retrieved by calling the passed closure.
    ///
    /// Whenever a new session is manually started via `startNewSession` method call, the closure is
    /// invoked to generate a new session ID.
    ///
    /// - parameter sessionIDGenerator: The closure that returns the session ID to use. Upon the
    ///                                 initialization of the logger the closure is called on the thread
    ///                                 that's used to configure the logger. Subsequent closure calls are
    ///                                 performed every time logger's `startNewSession` method is called
    ///                                 using the thread on which the method is called.
    ///
    /// - returns: The fixed session strategy.
    @objc
    public static func fixed(sessionIDGenerator: @escaping () -> String) -> SessionStrategyObjc {
        return SessionStrategyObjc(sessionStrategy: .fixed(sessionIDGenerator: sessionIDGenerator))
    }

    /// A session strategy that generates a new session ID after 30 minutes of app inactivity.
    ///
    /// The inactivity duration is measured by the minutes elapsed since the last log. The session ID is
    /// persisted to disk and survives app restarts.
    ///
    /// For this session strategy, each log emitted by the SDK — including those from session replay and
    /// resource monitoring feature — is considered an app activity.
    ///
    /// - returns: The activity based session strategy that expires session after 30 minutes of app
    ///            inactivity.
    @objc
    public static func activityBased() -> SessionStrategyObjc {
        return SessionStrategyObjc(sessionStrategy: .activityBased())
    }

    /// A session strategy that generates a new session ID after a certain period of app inactivity.
    ///
    /// The inactivity duration is measured by the minutes elapsed since the last log. The session ID is
    /// persisted to disk and survives app restarts.
    ///
    /// For this session strategy, each log emitted by the SDK — including those from session replay and
    /// resource monitoring feature — is considered an app activity.
    ///
    /// - parameter inactivityThresholdMins: The amount of minutes of inactivity after which a session ID
    ///                                      changes.
    ///
    /// - returns: The activity based session strategy that expires session after a specified duration of time
    ///            without any app activity.
    @objc
    public static func activityBased(inactivityThresholdMins: Int) -> SessionStrategyObjc {
        return SessionStrategyObjc(sessionStrategy: .activityBased(
            inactivityThresholdMins: inactivityThresholdMins,
            onSessionIDChanged: nil
        ))
    }

    /// A session strategy that generates a new session ID after a certain period of app inactivity.
    ///
    /// The inactivity duration is measured by the minutes elapsed since the last log. The session ID is
    /// persisted to disk and survives app restarts.
    ///
    /// For this session strategy, each log emitted by the SDK — including those from session replay and
    /// resource monitoring feature — is considered an app activity.
    ///
    /// - parameter inactivityThresholdMins: The amount of minutes of inactivity after which a session ID
    ///                                      changes.
    /// - parameter onSessionIDChange:       Closure that is invoked with the new value every time the session
    ///                                      ID changes. This callback is dispatched asynchronously to the
    ///                                      main queue.
    ///
    /// - returns: The activity based session strategy that expires session after a specified duration of time
    ///            without any app activity.
    @objc
    public static func activityBased(
        inactivityThresholdMins: Int,
        onSessionIDChange: @escaping (String) -> Void
    ) -> SessionStrategyObjc {
        return SessionStrategyObjc(sessionStrategy: .activityBased(
            inactivityThresholdMins: inactivityThresholdMins,
            onSessionIDChanged: onSessionIDChange
        ))
    }
}
