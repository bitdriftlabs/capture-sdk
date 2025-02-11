// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.util.Log
import com.github.michaelbull.result.Err
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Emits changes to [LoggerState] upon a call to Capture.Logger.start
 */
fun interface LoggerStateListener {
    /**
     * Reports on each [LoggerState] changes
     */
    fun onLoggerStateUpdate(loggerState: LoggerState)
}

/**
 * Represents the different Logger States
 */
sealed class LoggerState {
    /**
     * The logger has not yet been started.
     */
    data object NotStarted : LoggerState()

    /**
     * The logger is in the process of being started. Subsequent attempts to start the logger will be ignored.
     */
    data object Starting : LoggerState()

    /**
     * The logger has been successfully started and is ready for use. Subsequent attempts to start the logger will be ignored.
     */
    class Started(
        internal val logger: ILogger,
        /**
         * Reports the total duration of Capture.Logger.start
         */
        val startDuration: Duration,
        /**
         * Reports the thread where Capture.Logger.start took place
         */
        val callerThreadName: String,
    ) : LoggerState()

    /**
     * An attempt to start the logger was made but failed. Subsequent attempts to start the logger will be ignored.
     */
    class StartFailure(
        /**
         * The reason for Capture.Logger.start() failure
         */
        val throwable: Throwable,
    ) : LoggerState()
}

/**
 * Top level namespace Capture SDK.
 */
object Capture {
    private val default: AtomicReference<LoggerState> = AtomicReference(LoggerState.NotStarted)

    /**
     * Returns a handle to the underlying logger instance, if Capture has been started.
     *
     * @return ILogger a logger handle
     */
    fun logger(): ILogger? =
        when (val state = default.get()) {
            is LoggerState.NotStarted -> null
            is LoggerState.Starting -> null
            is LoggerState.Started -> state.logger
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
         * @param loggerStateListener optional listener that emits  [LoggerState] changes.
         *
         */
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
            loggerStateListener: LoggerStateListener? = null,
        ) {
            start(
                apiKey,
                sessionStrategy,
                configuration,
                fieldProviders,
                dateProvider,
                apiUrl,
                CaptureJniLibrary,
                loggerStateListener,
            )
        }

        @Synchronized
        @JvmStatic
        @JvmOverloads
        internal fun start(
            apiKey: String,
            sessionStrategy: SessionStrategy,
            configuration: Configuration = Configuration(),
            fieldProviders: List<FieldProvider> = listOf(),
            dateProvider: DateProvider? = null,
            apiUrl: HttpUrl = defaultCaptureApiUrl,
            bridge: IBridge,
            loggerStateListener: LoggerStateListener? = null,
        ) {
            // Note that we need to use @Synchronized to prevent multiple loggers from being initialized,
            // while subsequent logger access relies on volatile reads.

            // There's nothing we can do if we don't have yet access to the application context.
            if (!ContextHolder.isInitialized) {
                Log.w(
                    "capture",
                    "Attempted to initialize Capture before androidx.startup.Initializers " +
                        "are run. Aborting logger initialization.",
                )
                return
            }

            if (apiKey.isEmpty()) {
                val apiKeyException = IllegalArgumentException("API key is empty")
                default
                    .set(LoggerState.StartFailure(apiKeyException))
                    .apply {
                        loggerStateListener?.onLoggerStateUpdate(default.get())
                    }
                return
            }

            // Ideally we would use `getAndUpdate` in here but it's available for API 24 and up only.
            if (default.compareAndSet(LoggerState.NotStarted, LoggerState.Starting)) {
                loggerStateListener?.onLoggerStateUpdate(default.get())
                try {
                    val loggerCreationStartTime = System.currentTimeMillis()
                    val logger =
                        LoggerImpl(
                            apiKey = apiKey,
                            apiUrl = apiUrl,
                            fieldProviders = fieldProviders,
                            dateProvider = dateProvider ?: SystemDateProvider(),
                            configuration = configuration,
                            sessionStrategy = sessionStrategy,
                            bridge = bridge,
                        )
                    val loggerCreationTotalTime = (System.currentTimeMillis() - loggerCreationStartTime)
                    val elapsedDuration = loggerCreationTotalTime.toDuration(DurationUnit.MILLISECONDS)
                    default.set(LoggerState.Started(logger, elapsedDuration, Thread.currentThread().name))
                } catch (e: Throwable) {
                    Log.w("capture", "Failed to start Capture", e)
                    default.set(LoggerState.StartFailure(e))
                }
                loggerStateListener?.onLoggerStateUpdate(default.get())
            } else {
                Log.w("capture", "Multiple attempts to start Capture")
            }
        }

        /**
         * The Id for the current ongoing session.
         * It's equal to `null` prior to the start of Capture SDK.
         */
        @JvmStatic
        val sessionId: String?
            get() = logger()?.sessionId

        /**
         * The URL for the current ongoing session.
         * It's equal to `null` prior to the start of Capture SDK.
         */
        @JvmStatic
        val sessionUrl: String?
            get() = logger()?.sessionUrl

        /**
         * A canonical identifier for a device that remains consistent as long as an application
         * is not reinstalled.
         *
         * The value of this property is different for apps from the same vendor running on
         * the same device. It is equal to null prior to the start of bitdrift Capture SDK.
         */
        @JvmStatic
        val deviceId: String?
            get() = logger()?.deviceId

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
                mainThreadHandler.run { completion(Err(SdkNotStartedError)) }
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
            logger()?.let {
                it.removeField(key)
            }
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
         * @fields additional fields to include in the log.
         * @return a [Span] object that can be used to signal the end of the operation if Capture has been started.
         */
        @JvmStatic
        fun startSpan(
            name: String,
            level: LogLevel,
            fields: Map<String, String>? = null,
        ): Span? = logger()?.startSpan(name, level, fields)

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
         * Used for testing purposes.
         */
        internal fun resetShared() {
            default.set(LoggerState.NotStarted)
        }
    }
}
