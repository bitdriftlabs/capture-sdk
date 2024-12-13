// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

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
    /// - parameter apiURL:          The base URL of the Capture API. Rely on its default value unless
    ///                              specifically instructed otherwise during discussions with Bitdrift.
    ///                              Defaults to Bitdrift's hosted Compose API base
    /// - parameter enableNetworkIntegrations: Enables the URLSession network integration
    @objc
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategyObjc,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!,
        enableNetworkIntegrations: Bool = false
    ) {
        let integrator = Capture.Logger.start(
            withAPIKey: apiKey,
            sessionStrategy: sessionStrategy.underlyingSessionStrategy,
            apiURL: apiURL
        )

        if enableNetworkIntegrations {
            integrator?.enableIntegrations([.urlSession()])
        }
    }


    /// Defines the initialization of a new session within the current configured logger.
    /// If no logger is configured, this is a no-op.
    @objc
    public static func startNewSession() {
        Capture.Logger.shared?.startNewSession()
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

    /// Retrieves the session ID. It's equal to `nil` prior to the configuration of Capture SDK.
    ///
    /// - returns: The ID for the current ongoing session.
    @objc
    public static func sessionID() -> String? {
        return Capture.Logger.sessionID
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
