// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import android.util.Log
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

internal sealed class LoggerState {
    /**
     * The logger has not yet been started.
     */
    data object NotStarted : LoggerState()

    /**
     * The logger is in the process of being started. Subsequent attempts to start the logger will be ignored.
     * Any calls to Logger.log() meanwhile Logger.start() is in process will be cached in memory
     */
    class Starting(
        val preInitInMemoryLogger: PreInitInMemoryLogger,
    ) : LoggerState()

    /**
     * The logger has been successfully started and is ready for use. Subsequent attempts to start the logger will be ignored.
     */
    class Started(
        val loggerImpl: LoggerImpl,
    ) : LoggerState()

    /**
     * An attempt to start the logger was made but failed. Subsequent attempts to start the logger will be ignored.
     */
    data object StartFailure : LoggerState()
}

/**
 * Top level namespace Capture SDK.
 */
object Capture {
    internal const val LOG_TAG = "BitdriftCapture"
    private val default: AtomicReference<LoggerState> = AtomicReference(LoggerState.NotStarted)
    private val fatalIssueReporter = FatalIssueReporter()
    private val preInitInMemoryLogger by lazy { PreInitInMemoryLogger() }

    /**
     * Returns a handle to the underlying logger instance, if Capture has been started.
     *
     * @return ILogger a logger handle
     */
    fun logger(): ILogger? =
        when (val state = default.get()) {
            is LoggerState.NotStarted -> null
            is LoggerState.Starting -> state.preInitInMemoryLogger
            is LoggerState.Started -> state.loggerImpl
            is LoggerState.StartFailure -> null
        }

    /**
     * Top level entry point for the Capture SDK.
     *
     *  ## Logger Initialization
     *
     * To initialize the logger, first call Logger.start() with the desired configuration. Once this
     * call completes, logging can be done either via the top level logging functions or via a logger
     * handle acquired via Capture.logger().
     *
     * ## Logger Usage
     *
     * ```java
     * // Log line with LogLevel of Info
     * Logger.logInfo(mapOf("key" to "value")) { "Hello world!!" }
     *```
     */
    object Logger {
        private val defaultCaptureApiUrl =
            HttpUrl
                .Builder()
                .scheme("https")
                .host("api.bitdrift.io")
                .build()

        // This is a lazy property to avoid the need to initialize the main thread handler unless needed here
        private val mainThreadHandler by lazy { MainThreadHandler() }

        /**
         * Get the current version of the Capture library
         *
         * @return the version as a String
         */
        @JvmStatic
        val sdkVersion: String get() {
            return BuildConstants.SDK_VERSION
        }

        /**
         * WARNING: For now this API is not exposed to customers. If there is a request for this
         * will open visibility again
         *
         * Initializes fatal issue reporting mechanism that integrates
         * with existing configured crash libraries
         *
         * @param fatalIssueMechanism the [FatalIssueMechanism] to use for crash detection
         * @param context an optional context reference. You should provide the context if called from a [android.content.ContentProvider]
         *
         * This should be called prior to Capture.Logger.start()
         */
        @Suppress("UnusedPrivateMember")
        @ExperimentalBitdriftApi
        @JvmStatic
        private fun initIntegrationFatalIssueReporting(context: Context? = null) {
            if (context == null && !ContextHolder.isInitialized) {
                Log.w(LOG_TAG, "Attempted to initialize Fatal Issue Reporting with a null context. Skipping enabling crash tracking.")
                return
            }
            fatalIssueReporter.initIntegrationMode(context?.applicationContext ?: ContextHolder.APP_CONTEXT)
        }

        /**
         * Initializes the Capture SDK with the specified API key, providers, and configuration.
         * Calling other SDK methods has no effect unless the logger has been initialized.
         * Subsequent calls to this function will have no effect.
         *
         * @param apiKey The API key provided by bitdrift. This is required.
         * @param sessionStrategy session strategy for the management of session id.
         * @param configuration A configuration that is used to set up Capture features.
         * @param fieldProviders list of extra field providers to apply to all logs.
         * @param dateProvider optional date provider used to override how the current timestamp is computed.
         * @param apiUrl The base URL of Capture API. Depend on its default value unless specifically
         *               instructed otherwise during discussions with bitdrift. Defaults to bitdrift's hosted
         *               Compose API base URL.
         * @param context an optional context reference. You should provide the context if called from
         * a [android.content.ContentProvider]
         */
        @Deprecated(
            message = "start is deprecated, please use startAsync",
            replaceWith =
                ReplaceWith(
                    expression =
                        "startAsync(apiKey, sessionStrategy, completion, configuration, " +
                            "fieldProviders, completion, dateProvider, apiUrl, context)",
                ),
            level = DeprecationLevel.WARNING,
        )
        @Synchronized
        @JvmStatic
        @JvmOverloads
        fun start(
            apiKey: String,
            sessionStrategy: SessionStrategy,
            configuration: Configuration = Configuration(),
            fieldProviders: List<FieldProvider> = listOf(),
            dateProvider: DateProvider? = null,
            apiUrl: HttpUrl = defaultCaptureApiUrl,
            context: Context? = null,
        ) {
            performStart(
                apiKey,
                sessionStrategy,
                configuration,
                fieldProviders,
                dateProvider,
                apiUrl,
                CaptureJniLibrary,
                context,
            )
        }

        /**
         * Initializes the Capture SDK in a dedicated background thread with the specified API key, providers, and configuration.
         *
         * Will emit a completion of [io.bitdrift.capture.CaptureResult] when is startAsync is done.
         *
         * Calling other SDK methods has no effect unless the logger has been initialized.
         * Subsequent calls to this function will have no effect.
         *
         * @param apiKey The API key provided by bitdrift. This is required.
         * @param sessionStrategy session strategy for the management of session id.
         * @param completion The callback that is called when the start async operation is complete. Called on the main
         *                   thread.
         * @param configuration A configuration that is used to set up Capture features.
         * @param fieldProviders list of extra field providers to apply to all logs.
         * @param dateProvider optional date provider used to override how the current timestamp is computed.
         * @param apiUrl The base URL of Capture API. Depend on its default value unless specifically
         *               instructed otherwise during discussions with bitdrift. Defaults to bitdrift's hosted
         *               Compose API base URL.
         * @param context an optional context reference. You should provide the context if called from
         * a [android.content.ContentProvider]
         */
        @Synchronized
        @JvmStatic
        @JvmOverloads
        fun startAsync(
            apiKey: String,
            sessionStrategy: SessionStrategy,
            completionResult: (CaptureResult<Unit>) -> Unit,
            configuration: Configuration = Configuration(),
            fieldProviders: List<FieldProvider> = listOf(),
            dateProvider: DateProvider? = null,
            apiUrl: HttpUrl = defaultCaptureApiUrl,
            context: Context? = null,
        ) {
            performStart(
                apiKey,
                sessionStrategy,
                configuration,
                fieldProviders,
                dateProvider,
                apiUrl,
                CaptureJniLibrary,
                context,
                CaptureDispatchers.CommonBackground,
                completionResult,
            )
        }

        @Synchronized
        @JvmStatic
        @JvmOverloads
        internal fun performStart(
            apiKey: String,
            sessionStrategy: SessionStrategy,
            configuration: Configuration = Configuration(),
            fieldProviders: List<FieldProvider> = listOf(),
            dateProvider: DateProvider? = null,
            apiUrl: HttpUrl = defaultCaptureApiUrl,
            bridge: IBridge,
            context: Context? = null,
            backgroundThreadHandler: IBackgroundThreadHandler? = null,
            completionResult: ((CaptureResult<Unit>) -> Unit)? = null,
        ) {
            // Note that we need to use @Synchronized to prevent multiple loggers from being initialized,
            // while subsequent logger access relies on volatile reads.

            // There's nothing we can do if we don't have yet access to the application context.
            if (context == null && !ContextHolder.isInitialized) {
                val errorMessage = "Attempted to initialize Capture with a null context"
                Log.w(LOG_TAG, errorMessage)
                completionResult.reportFailure(errorMessage)
                return
            }

            // Ideally we would use `getAndUpdate` in here but it's available for API 24 and up only.
            if (default.compareAndSet(LoggerState.NotStarted, LoggerState.Starting(preInitInMemoryLogger))) {
                val initBlock: () -> Unit = {
                    try {
                        val unwrappedContext = context?.applicationContext ?: ContextHolder.APP_CONTEXT
                        if (configuration.enableFatalIssueReporting) {
                            fatalIssueReporter.initBuiltInMode(unwrappedContext)
                        }
                        val loggerImpl =
                            LoggerImpl(
                                apiKey = apiKey,
                                apiUrl = apiUrl,
                                context = unwrappedContext,
                                fieldProviders = fieldProviders,
                                dateProvider = dateProvider ?: SystemDateProvider(),
                                configuration = configuration,
                                sessionStrategy = sessionStrategy,
                                bridge = bridge,
                                fatalIssueReporter = fatalIssueReporter,
                                preInitLogFlusher = preInitInMemoryLogger,
                            )
                        default.set(LoggerState.Started(loggerImpl))
                        completionResult.reportSuccess()
                    } catch (throwable: Throwable) {
                        val throwableMessage = throwable.message?.let { ". $it" } ?: ""
                        val errorDetails = "Failed to start Capture$throwableMessage"
                        Log.w(LOG_TAG, errorDetails, throwable)
                        preInitInMemoryLogger.clear()
                        completionResult.reportFailure(errorDetails)
                        default.set(LoggerState.StartFailure)
                    }
                }

                if (backgroundThreadHandler != null) {
                    backgroundThreadHandler.runAsync(initBlock)
                } else {
                    initBlock()
                }
            } else {
                Log.w(LOG_TAG, "Multiple attempts to start Capture")
            }
        }

        /**
         * The Id for the current ongoing session.
         * It's equal to `null` prior to the start of Capture SDK.
         */
        @JvmStatic
        val sessionId: String?
            get() = (logger() as? LoggerImpl)?.sessionId

        /**
         * The URL for the current ongoing session.
         * It's equal to `null` prior to the start of Capture SDK.
         */
        @JvmStatic
        val sessionUrl: String?
            get() = (logger() as? LoggerImpl)?.sessionUrl

        /**
         * A canonical identifier for a device that remains consistent as long as an application
         * is not reinstalled.
         *
         * The value of this property is different for apps from the same vendor running on
         * the same device. It is equal to null prior to the start of bitdrift Capture SDK.
         */
        @JvmStatic
        val deviceId: String?
            get() = (logger() as? LoggerImpl)?.deviceId

        /**
         * Defines the initialization of a new session within the currently running logger
         * If no logger is started, this is a no-op.
         */
        @JvmStatic
        fun startNewSession() {
            logger()?.startNewSession()
        }

        /**
         * Creates a temporary device code that can be fed into other bitdrift tools to stream logs from a
         * given device in real-time fashion. The creation of the device code requires communication with
         * the bitdrift remote service.
         *
         * @param completion The callback that is called when the operation is complete. Called on the
         * main thread.
         */
        @JvmStatic
        fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) {
            logger()?.also {
                it.createTemporaryDeviceCode {
                    mainThreadHandler.run { completion(it) }
                }
            } ?: run {
                mainThreadHandler.run { completion(CaptureResult.Failure(SdkNotStartedError())) }
            }
        }

        /**
         * Adds a field that should be attached to all logs emitted by the logger going forward.
         * If a field with a given key has already been registered with the logger, its value is
         * overridden with the new value.
         *
         * Fields added with this method take precedence over fields returned by registered `FieldProvider`s
         * and are overwritten by custom logs emitted.
         *
         * @param key the name of the field to add.
         * @param value the value of the field to add.
         */
        @JvmStatic
        fun addField(
            key: String,
            value: String,
        ) {
            logger()?.let {
                it.addField(key, value)
            }
        }

        /**
         * Removes a field with a given key. This operation does nothing if the field
         * with the given key is not registered with the logger.
         *
         * @param key the name of the field to remove.
         */
        @JvmStatic
        fun removeField(key: String) {
            logger()?.removeField(key)
        }

        /**
         * Logs a message at trace level.
         *
         * @param fields an optional map of additional data to include with the log.
         * @param throwable an optional throwable to include with the log.
         * @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun logTrace(
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = LogLevel.TRACE, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Logs a message at debug level.
         *
         * @param fields an optional map of additional data to include with the log.
         * @param throwable an optional throwable to include with the log.
         * @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun logDebug(
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = LogLevel.DEBUG, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Logs a message at info level.
         *
         * @param fields an optional map of additional data to include with the log.
         * @param throwable an optional throwable to include with the log.
         * @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun logInfo(
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = LogLevel.INFO, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Logs a message at warning level.
         *
         * @param fields an optional map of additional data to include with the log.
         * @param throwable an optional throwable to include with the log.
         * @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun logWarning(
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = LogLevel.WARNING, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Logs a message at error level.
         *
         * @param fields an optional map of additional data to include with the log.
         * @param throwable an optional throwable to include with the log.
         * @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun logError(
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = LogLevel.ERROR, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Logs a message at a specified level.
         *
         *  @param level the severity of the log.
         *  @param fields an optional map of additional data to include with the log.
         *  @param throwable an optional throwable to include with the log.
         *  @param message the message to log.
         */
        @JvmStatic
        @JvmOverloads
        fun log(
            level: LogLevel,
            fields: Map<String, String>? = null,
            throwable: Throwable? = null,
            message: () -> String,
        ) {
            logger()?.log(level = level, fields = fields, throwable = throwable, message = message)
        }

        /**
         * Writes an app launch TTI log event. This event should be logged only once per Logger start.
         * Consecutive calls have no effect.
         *
         * @param duration The time between a user's intent to launch the app and when the app becomes
         *                 interactive. Calls with a negative duration are ignored.
         */
        @JvmStatic
        fun logAppLaunchTTI(duration: Duration) {
            logger()?.logAppLaunchTTI(duration)
        }

        /**
         * Logs a screen view event.
         *
         * @param screenName the name of the screen.
         */
        @JvmStatic
        fun logScreenView(screenName: String) {
            logger()?.logScreenView(screenName)
        }

        /**
         * Signals that an operation has started at this point in time.
         *
         * @param name the name of the operation.
         * @param level the severity of the log.
         * @param fields additional fields to include in the log.
         * @param startTimeMs an optional custom start time in milliseconds since the Unix epoch. This can be
         *                    used to override the default start time of the span. If provided, it needs
         *                    to be used in combination with an `endTimeMs`. Providing one and not the other is
         *                    considered an error and in that scenario, the default clock will be used instead.
         * @param parentSpanId: an optional ID of the parent span, used to build span hierarchies. A span
         *                      without a parentSpanId is considered a root span.
         * @return a [Span] object that can be used to signal the end of the operation if Capture has been started.
         */
        @JvmStatic
        fun startSpan(
            name: String,
            level: LogLevel,
            fields: Map<String, String>? = null,
            startTimeMs: Long? = null,
            parentSpanId: UUID? = null,
        ): Span? = logger()?.startSpan(name, level, fields, startTimeMs, parentSpanId)

        /**
         * Wrap the specified [block] in calls to [startSpan] (with the supplied params)
         * and [Span.end]. The result will be based on whether [block] threw an exception or not.
         *
         * @param name the name of the operation.
         * @param level the severity of the log.
         * @param fields additional fields to include in the log.
         * @param block A block of code which is being tracked.
         */
        @JvmStatic
        inline fun <T> trackSpan(
            name: String,
            level: LogLevel,
            fields: Map<String, String>? = null,
            crossinline block: () -> T,
        ): T {
            val span = this.startSpan(name, level, fields)
            try {
                val result = block()
                span?.end(SpanResult.SUCCESS)
                return result
            } catch (exception: Throwable) {
                span?.end(SpanResult.FAILURE)
                throw exception
            }
        }

        /**
         * Logs a network HTTP request event.
         *
         * @param httpRequestInfo information about the HTTP request
         */
        @JvmStatic
        fun log(httpRequestInfo: HttpRequestInfo) {
            logger()?.log(httpRequestInfo)
        }

        /**
         * Logs a network HTTP response event. This should be called once a request logged via
         * `log` has completed to indicate its result.
         *
         * @param httpResponseInfo information about the HTTP response
         */
        @JvmStatic
        fun log(httpResponseInfo: HttpResponseInfo) {
            logger()?.log(httpResponseInfo)
        }

        /**
         * Sets the operation mode of the logger, where activating sleep mode
         * reduces activity to a minimal level
         *
         * @param sleepMode mode to use
         */
        @JvmStatic
        fun setSleepMode(sleepMode: SleepMode) {
            logger()?.setSleepMode(sleepMode)
        }

        /**
         * Used for testing purposes.
         */
        internal fun resetShared() {
            default.set(LoggerState.NotStarted)
        }

        private fun ((CaptureResult<Unit>) -> Unit)?.reportSuccess() {
            this?.let {
                mainThreadHandler.run {
                    it.invoke(CaptureResult.Success(Unit))
                }
            }
        }

        private fun ((CaptureResult<Unit>) -> Unit)?.reportFailure(message: String) {
            this?.let {
                mainThreadHandler.run {
                    it.invoke(CaptureResult.Failure(SdkNotStartedError(message)))
                }
            }
        }
    }
}
