// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import java.util.UUID
import kotlin.time.Duration

/**
 * A Capture SDK logger interface.
 */
interface ILogger {
    /**
     * The identifier for the current ongoing session.
     */
    val sessionId: String

    /**
     * The URL for the current ongoing session.
     */
    val sessionUrl: String

    /**
     * A canonical identifier for a device that remains consistent as long as an application
     * is not reinstalled.
     *
     * The value of this property is different for apps from the same vendor running on
     * the same device.
     */
    val deviceId: String

    /**
     * Defines the initialization of a new session within the current logger.
     */
    fun startNewSession()

    /**
     * Creates a temporary device code that can be fed into other bitdrift tools to stream logs from a
     * given device in real-time fashion. The creation of the device code requires communication with
     * the bitdrift remote service.
     *
     * @param completion The callback that is called when the operation is complete. Called on the
     * main thread.
     */
    fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit)

    /**
     * Adds a field to all logs emitted by the logger from this point forward.
     * If a field with a given key has already been registered with the logger, its value is
     * replaced with the new one.
     *
     * Fields added with this method take precedence over fields provided by registered `FieldProvider`s
     * and are overwritten by fields provided with custom logs.
     *
     * @param key the name of the field to add.
     * @param value the value of the field to add.
     */
    fun addField(
        key: String,
        value: String,
    )

    /**
     * Removes a field with a given key. This operation has no effect if a field with the given key
     * is not registered with the logger.
     *
     * @param key the name of the field to remove.
     */
    fun removeField(key: String)

    /**
     * Logs a message at a specified level.
     *
     * @param level the severity of the log.
     * @param fields and optional collection of key-value pairs to be added to the log line.
     * @param throwable an optional throwable to include in the log line.
     * @param message the main message of the log line, the lambda gets evaluated lazily.
     */
    fun log(
        level: LogLevel,
        fields: Map<String, String>? = null,
        throwable: Throwable? = null,
        message: () -> String,
    )

    /**
     * Writes an app launch TTI log event. This event should be logged only once per Logger configuration.
     * Consecutive calls have no effect.
     *
     * @param duration The time between a user's intent to launch the app and when the app becomes
     *                 interactive. Calls with a negative duration are ignored.
     */
    fun logAppLaunchTTI(duration: Duration)

    /**
     * Logs a screen view event.
     *
     * @param screenName the name of the screen.
     */
    fun logScreenView(screenName: String)

    /**
     * Signals that an operation has started at this point in time. Each operation consists of start
     * and end event logs. The start event is emitted immediately upon calling the
     * `Logger.startSpan(...)` method, while the corresponding end event is emitted when the
     * `end(...)` method is called on the [Span] returned from the method. Refer to [Span] for
     * more details.
     *
     * @param name the name of the operation.
     * @param level the severity of the log.
     * @param fields additional fields to include in the log.
     * @param startTimeMs An optional custom start time to use in combination with an `endTimeMs`
     *                    at span end to calculate duration. Providing one and not the other is
     *                    considered an error and in that scenario, the default clock will be used instead.
     * @param parentSpanId: An optional ID of the parent span, used to build span hierarchies. A span
     *                      without a parentSpanId is considered a root span.
     * @param emitStartLog a boolean indicating if the span start log needs to be sent or not.
     *
     * @return a [Span] object that can be used to signal the end of the operation.
     */
    fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>? = null,
        startTimeMs: Long? = null,
        parentSpanId: UUID? = null,
        emitStartLog: Boolean = true,
    ): Span

    /**
     * Similar to `startSpan` but uses a known start and end intervals. It's worth noting that calling
     * this function is not the same as calling `startSpan` and `end` one after the other since in
     * this case we'll only send one `end` log with the duration derived from the given times.
     * Also worth noting the timestamp of the log itself emitted will not be based on the provided intervals.
     *
     * @param name the name of the operation.
     * @param level the severity of the log.
     * @param fields additional fields to include in the log.
     * @param result the result of the operation.
     * @param startTimeMs the start time interval to use in combination with `endTimeInterval`
     *                    to calculate duration.
     * @param endTimeMs the end time to use in combination with the `startTimeInterval` to calculate
     *                  the span duration.
     * @param parentSpanId an optional ID of the parent span, used to build span hierarchies. A span
     *                     without a parentSpanID is considered a root span.
     */
    fun logSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>? = null,
        result: SpanResult,
        startTimeMs: Long,
        endTimeMs: Long,
        parentSpanId: UUID? = null,
    )

    /**
     * Records information about an HTTP network request
     *
     * @param httpRequestInfo information used to enrich the log line
     */
    fun log(httpRequestInfo: HttpRequestInfo)

    /**
     * Records information about an HTTP network response
     *
     * @param httpResponseInfo information used to enrich the log line
     */
    fun log(httpResponseInfo: HttpResponseInfo)
}
