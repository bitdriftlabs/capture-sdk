// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation
import UIKit

/// A map of log fields.
public typealias Fields = [String: FieldValue]
/// The value of a log field.
public typealias FieldValue = Encodable & Sendable

extension Logger {
    struct SDKNotStartedfError: Swift.Error {}

    // MARK: - General

    /// An instance of the underlying logger, if the Capture SDK is started. Returns `nil` if the
    /// `start(...)` method has not been called or if starting the logger process failed due to an internal
    /// error.
    public static var shared: Logging? {
        self.getShared()
    }

    /// Initializes the Capture SDK with the specified API key, providers, and configuration.
    /// Calling other SDK methods has no effect unless the logger has been initialized.
    /// Subsequent calls to this function will have no effect.
    ///
    /// - parameter apiKey:          The API key provided by bitdrift.
    /// - parameter sessionStrategy: A session strategy for the management of session ID.
    /// - parameter configuration:   A configuration that used to set up Capture features.
    /// - parameter fieldProviders:  An optional array of additional FieldProviders to include on the default
    ///                              Logger.
    /// - parameter dateProvider:    An optional date provider to set on the default logger.
    /// - parameter apiURL:          The base URL of Capture API. Depend on its default value unless
    ///                              specifically
    ///                              instructed otherwise during discussions with bitdrift. Defaults to
    ///                              bitdrift's hosted Compose API base URL.
    ///
    /// - returns: A logger integrator that can be used to enable various SDK integration.
    @discardableResult
    public static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategy,
        configuration: Configuration = .init(),
        fieldProviders: [FieldProvider] = [],
        dateProvider: DateProvider? = nil,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!
    ) -> LoggerIntegrator?
    {
        return self.start(
            withAPIKey: apiKey,
            sessionStrategy: sessionStrategy,
            configuration: configuration,
            fieldProviders: fieldProviders,
            dateProvider: dateProvider,
            apiURL: apiURL,
            loggerBridgingFactoryProvider: LoggerBridgingFactory()
        )
    }

    @discardableResult
    static func start(
        withAPIKey apiKey: String,
        sessionStrategy: SessionStrategy,
        configuration: Configuration,
        fieldProviders: [FieldProvider],
        dateProvider: DateProvider?,
        apiURL: URL,
        loggerBridgingFactoryProvider: LoggerBridgingFactoryProvider
    ) -> LoggerIntegrator?
    {
        return self.createOnce {
            return Logger(
                withAPIKey: apiKey,
                apiURL: apiURL,
                configuration: configuration,
                sessionStrategy: sessionStrategy,
                dateProvider: dateProvider,
                fieldProviders: fieldProviders,
                loggerBridgingFactoryProvider: loggerBridgingFactoryProvider
            )
        }
    }

    /// Retrieves the session ID. It is nil before the Capture SDK is started.
    public static var sessionID: String? {
        return Self.getShared()?.sessionID
    }

    /// Retrieves the session URL. It is `nil` before the Capture SDK is started.
    public static var sessionURL: String? {
        return Self.getShared()?.sessionURL
    }

    /// Initializes a new session within the currently configured logger.
    /// The logger must be started before this operation for it to take effect.
    public static func startNewSession() {
        Self.getShared()?.startNewSession()
    }

    /// A canonical identifier for a device that remains consistent as long as an application
    /// is not reinstalled.
    ///
    /// The value of this property is different for apps from the same vendor running on
    /// the same device. It is equal to `nil` prior to the start of bitdrift Capture SDK.
    public static var deviceID: String? {
        return Self.getShared()?.deviceID
    }

    // MARK: - Logging

    /// Logs a message at a specified level to the default logger instance.
    ///
    /// - parameter level:    The severity of the log.
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        Self.getShared()?.log(
            level: level,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs a trace level message to the default logger instance.
    ///
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func logTrace(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    )
    {
        Self.getShared()?.log(
            level: .trace,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs a debug level message to the default logger instance.
    ///
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func logDebug(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    )
    {
        Self.getShared()?.log(
            level: .debug,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs an info level message to the default logger instance.
    ///
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func logInfo(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    )
    {
        Self.getShared()?.log(
            level: .info,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs a warning level message to the default logger instance.
    ///
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func logWarning(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    )
    {
        Self.getShared()?.log(
            level: .warning,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs an error level message to the default logger instance.
    ///
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    public static func logError(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    )
    {
        Self.getShared()?.log(
            level: .error,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    // MARK: - Predefined Logs

    /// Writes an app launch TTI log event. This event should be logged only once per Logger start.
    /// Consecutive calls have no effect.
    ///
    /// - parameter duration: The time between a user's intent to launch the app and when the app becomes
    ///                       interactive. Calls with a negative duration are ignored.
    public static func logAppLaunchTTI(_ duration: TimeInterval) {
        Self.getShared()?.logAppLaunchTTI(duration)
    }

    /// Writes a log that indicates that a "screen" has been presented. This is useful for snakeys and other journeys visualization.
    ///
    /// - parameter screenName: The human readable unique identifier of the screen being presented.
    public static func logScreenView(screenName: String) {
        Self.getShared()?.logScreenView(screenName: screenName)
    }

    // MARK: - Network Activity Logging

    /// Logs information about a network request.
    ///
    /// - parameter request:  The request to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    public static func log(
        _ request: HTTPRequestInfo,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function
    ) {
        Self.getShared()?.log(request, file: file, line: line, function: function)
    }

    /// Logs information about a network response.
    ///
    /// - parameter response: The response to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    public static func log(
        _ response: HTTPResponseInfo,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function
    ) {
        Self.getShared()?.log(response, file: file, line: line, function: function)
    }

    // MARK: - Spans

    /// Signals that an operation has started at this point in time. Each operation consists of start and
    /// end event logs. The start event is emitted immediately upon calling the `startSpan(...)` method,
    /// while the corresponding end event is emitted when the `end(...)` method is called on the `Span`
    /// returned from the method. Refer to `Span` for more details.
    ///
    /// - parameter name:              The name of the operation.
    /// - parameter level:             The severity of the log to use when emitting logs for the operation.
    /// - parameter file:              The unique file identifier that has the form module/file.
    /// - parameter line:              The line number where the log is emitted.
    /// - parameter function:          The name of the function from which the log is emitted.
    /// - parameter fields:            The extra fields to send as part of start and end logs for the operation.
    /// - parameter startTimeInterval: An optional custom start time to use in combination with an `endTimeInterval`
    ///                                at span end to calculate duration. Providing one and not the other is considered an
    ///                                error and in that scenario, the default time provider will be used instead.
    /// - parameter parentSpanID:      An optional ID of the parent span, used to build span hierarchies. A span without a
    ///                                parentSpanID is considered a root span.
    ///
    /// - returns: A span that can be used to signal the end of the operation if the Capture SDK has been
    ///            started. Returns `nil` if the `start(...)` method has not been called.
    public static func startSpan(
        name: String,
        level: LogLevel,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        startTimeInterval: TimeInterval? = nil,
        parentSpanID: UUID? = nil
    ) -> Span? {
        Self.getShared()?.startSpan(
            name: name, level: level, file: file, line: line, function: function, fields: fields,
            startTimeInterval: startTimeInterval, parentSpanID: parentSpanID
        )
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
    public static func addField(withKey key: String, value: FieldValue) {
        Self.getShared()?.addField(withKey: key, value: value)
    }

    /// Removes a field with a given key. This operation has no effect if a field with the given key
    /// is not registered with the logger.
    ///
    /// - parameter key: The name of the field to remove.
    public static func removeField(withKey key: String) {
        Self.getShared()?.removeField(withKey: key)
    }

    /// Creates a temporary device code that can be fed into other bitdrift tools to stream logs from a
    /// given device in real-time fashion. The creation of the device code requires communication with
    /// the bitdrift remote service.
    ///
    /// - parameter completion: The closure that is called when the operation is complete. Called on the
    ///                         main queue.
    public static func createTemporaryDeviceCode(completion: @escaping (Result<String, Error>) -> Void) {
        if let logger = Self.getShared() {
            logger.createTemporaryDeviceCode { result in
                DispatchQueue.main.async {
                    completion(result)
                }
            }
        } else {
            DispatchQueue.main.async {
                completion(.failure(SDKNotStartedfError()))
            }
        }
    }
}
