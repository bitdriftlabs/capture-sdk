// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A Capture SDK logger interface.
public protocol Logging {
    /// Retrieves the session ID.
    var sessionID: String { get }

    /// Retrieves the session URL.
    var sessionURL: String { get }

    /// Initializes a new session within the currently configured logger.
    func startNewSession()

    /// A canonical identifier for a device that remains consistent as long as an application
    /// is not reinstalled.
    ///
    /// The value of this property is different for apps from the same vendor running on
    /// the same device.
    var deviceID: String { get }

    /// Logs a message at a specified level to the default logger instance.
    ///
    /// - parameter level:    The severity of the log.
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        error: Error?
    )

    /// Logs information about a network request.
    ///
    /// - parameter request:  The request to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    func log(
        _ request: HTTPRequestInfo,
        file: String?,
        line: Int?,
        function: String?
    )

    /// Logs information about a network response.
    ///
    /// - parameter response: The response to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    func log(
        _ response: HTTPResponseInfo,
        file: String?,
        line: Int?,
        function: String?
    )

    /// Adds a field to all logs emitted by the logger from this point forward.
    /// If a field with a given key has already been registered with the logger, its value is
    /// replaced with the new one.
    ///
    /// Fields added with this method take precedence over fields provided by registered `FieldProvider`s
    /// and are overwritten by fields provided with custom logs.
    ///
    /// - parameter key:   The name of the field to add.
    /// - parameter value: The value of the field to add.
    func addField(withKey key: String, value: FieldValue)

    /// Removes a field with a given key. This operation has no effect if a field with the given key
    /// is not registered with the logger.
    ///
    /// - parameter key: The name of the field to remove.
    func removeField(withKey key: String)

    /// Creates a temporary device code that can be fed into bitdrift `bd` CLI tools to stream logs from a
    /// given device in real-time fashion. The creation of the device code requires communication with
    /// the bitdrift remote service.
    ///
    /// - parameter completion: The closure that is called when the operation is complete. Called on the
    ///                         main queue.
    func createTemporaryDeviceCode(completion: @escaping (Result<String, Error>) -> Void)

    // MARK: - Predefined logs

    /// Writes an app launch TTI log event. This event should be logged only once per Logger configuration.
    /// Consecutive calls have no effect.
    ///
    /// - parameter duration: The time between a user's intent to launch the app and when the app becomes
    ///                       interactive. Calls with a negative duration are ignored.
    func logAppLaunchTTI(_ duration: TimeInterval)

    /// Logs a screen view event.
    ///
    /// - parameter screenName: The name of the screen.
    func logScreenView(screenName: String)

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
    /// - parameter emitStartEvent:    A boolean indicating if the span start log needs to be sent or not.
    ///
    /// - returns: A span that can be used to signal the end of the operation if the Capture SDK has been
    ///            configured.
    func startSpan(
        name: String, level: LogLevel, file: String?, line: Int?, function: String?,
        fields: Fields?, startTimeInterval: TimeInterval?, parentSpanID: UUID?, emitStartEvent: Bool
    ) -> Span

    /// Similar to `startSpan` but uses a known start and end intervals. It's worth noting that calling this function is not the same as
    /// calling `startSpan` and `end` one after the other since in this case we'll only send one `end` log with the duration
    /// derived from the given times. Also worth noting the timestamp of the log itself emitted will not be based on the provided intervals.
    ///
    /// - parameter name:              The name of the operation.
    /// - parameter level:             The severity of the log to use when emitting logs for the operation.
    /// - parameter result:            The result of the operation.
    /// - parameter file:              The unique file identifier that has the form module/file.
    /// - parameter line:              The line number where the log is emitted.
    /// - parameter function:          The name of the function from which the log is emitted.
    /// - parameter startTimeInterval: An optional custom start time to use in combination with an `endTimeInterval`
    ///                                at span end to calculate duration. Providing one and not the other is considered an
    ///                                error and in that scenario, the default time provider will be used instead.
    /// - parameter endTimeInterval:   An optional custom end time to use in combination with the `startTimeInterval`
    ///                                provided when creating the span. Setting one and not the other is considered an error
    ///                                and in that scenario, Capture's time provider will be used instead.
    /// - parameter parentSpanID:      An optional ID of the parent span, used to build span hierarchies. A span without a
    ///                                parentSpanID is considered a root span.
    /// - parameter fields:            The extra fields to send as part of start and end logs for the operation.
    func logSpan(
        name: String, level: LogLevel, result: SpanResult, file: String?, line: Int?,
        function: String?, startTimeInterval: TimeInterval, endTimeInterval: TimeInterval,
        parentSpanID: UUID?, fields: Fields?
    )
}

extension Logging {
    /// Logs a message at a specified level to the default logger instance. Provides default values for
    /// `file`, `line`, and `function` parameters.
    ///
    /// - parameter level:    The severity of the log.
    /// - parameter message:  The message to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number where the log is emitted.
    /// - parameter function: The name of the function from which the log is emitted.
    /// - parameter fields:   The extra fields to send with the log.
    /// - parameter error:    The error to log.
    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
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
    public func logTrace(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
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
    public func logDebug(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
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
    public func logInfo(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
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
    public func logWarning(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
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
    public func logError(
        _ message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        error: Error? = nil
    ) {
        self.log(
            level: .error,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error
        )
    }

    /// Logs information about a network request. Provides default values for
    /// `file`, `line`, and `function` parameters.
    ///
    /// - parameter request:  The request to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number on which the log is emitted.
    /// - parameter function: The name of the declaration from within which the log is emitted.
    public func log(
        _ request: HTTPRequestInfo,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function
    ) {
        self.log(request, file: file, line: line, function: function)
    }

    /// Logs information about a network response. Provides default values for
    /// `file`, `line`, and `function` parameters.
    ///
    /// - parameter response: The response to log.
    /// - parameter file:     The unique file identifier that has the form module/file.
    /// - parameter line:     The line number on which the log is emitted.
    /// - parameter function: The name of the declaration from within which the log is emitted.
    public func log(
        _ response: HTTPResponseInfo,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function
    ) {
        self.log(response, file: file, line: line, function: function)
    }
}
