// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.strictmode.Violation
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture.Logger.startSpan
import io.bitdrift.capture.LoggerImpl.SdkConfiguredDuration
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
import io.bitdrift.capture.reports.exitinfo.PreviousRunInfo
import io.bitdrift.capture.utils.BuildTypeChecker
import io.bitdrift.capture.utils.DebugCustomerCallbackException
import io.bitdrift.capture.utils.invokeCatchingOrThrowOnDebug
import okhttp3.HttpUrl
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal sealed class LoggerState {
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
        val logger: LoggerImpl,
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
    private val preInitInMemoryLoggerRef: AtomicReference<PreInitInMemoryLogger?> =
        AtomicReference()

    /**
     * Returns a handle to the underlying logger instance, if Capture has been started.
     *
     * @return ILogger a logger handle
     */
    fun logger(): ILogger? =
        when (val state = default.get()) {
            is LoggerState.NotStarted -> null
            is LoggerState.Starting -> preInitInMemoryLoggerRef.get()
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

        // This is a lazy property to avoid the need to initialize the main thread handler unless needed here.
        private val mainThreadHandler by lazy { MainThreadHandler() }

        /**
         * Get the current version of the Capture library.
         *
         * @return the version as a String
         */
        @JvmStatic
        val sdkVersion: String
            get() {
                return BuildConstants.SDK_VERSION
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
         * a [android.content.ContentProvider].
         * @param executor optional executor used to run the internal SDK initialization off the calling thread.
         *                 When null, initialization runs synchronously on the calling thread.
         * @param startResult an optional callback invoked with the result of the SDK initialization.
         *                     When no [executor] is provided, the callback is invoked on the calling thread
         *                     before [start] returns. When an [executor] is provided, [start] returns
         *                     immediately after submitting the init task, and the callback is invoked on the
         *                     executor thread once initialization completes.
         *                     On success, it receives a [CaptureResult.Success] containing an [ILogger] instance.
         *                     On failure, it receives a [CaptureResult.Failure] with a [SdkStartFailure].
         */
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
            executor: Executor? = null,
            startResult: ((CaptureResult<ILogger>) -> Unit)? = null,
        ) {
            start(
                apiKey,
                sessionStrategy,
                configuration,
                fieldProviders,
                dateProvider,
                apiUrl,
                CaptureJniLibrary,
                context,
                executor,
                startResult,
            )
        }

        // Single-initialization is enforced by the atomic CAS on `default` below;
        // no external locking is required. Callers observe the started LoggerImpl via
        // `Capture.logger()`, published through the AtomicReference (volatile-equivalent semantics).
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
            context: Context? = null,
            executor: Executor? = null,
            startResult: ((CaptureResult<ILogger>) -> Unit)? = null,
        ) {
            // There's nothing we can do if we don't have yet access to the application context.
            if (hasInvalidContext(context)) {
                val errorMessage = "Attempted to initialize Capture with a null context"
                Log.w(LOG_TAG, errorMessage)
                dispatchStartResult(
                    executor = executor,
                    startResult = startResult,
                    result = CaptureResult.Failure(SdkStartFailure(errorMessage)),
                )
                return
            }

            // Ideally we would use `getAndUpdate` in here but it's available for API 24 and up only.
            // Only the CAS winner performs any side effects; concurrent/repeat callers are a no-op.
            if (!default.compareAndSet(LoggerState.NotStarted, LoggerState.Starting)) {
                Log.w(LOG_TAG, "Multiple attempts to start Capture")
                return
            }

            if (executor != null) {
                // Install the pre-init buffer so log calls made between now and `Started`
                // are captured. `initSdk` is responsible for draining and clearing it.
                preInitInMemoryLoggerRef.set(PreInitInMemoryLogger())
                executor.execute {
                    initSdk(
                        apiKey = apiKey,
                        sessionStrategy = sessionStrategy,
                        configuration = configuration,
                        fieldProviders = fieldProviders,
                        dateProvider = dateProvider,
                        apiUrl = apiUrl,
                        bridge = bridge,
                        context = context,
                        startResult = startResult,
                    )
                }
            } else {
                initSdk(
                    apiKey = apiKey,
                    sessionStrategy = sessionStrategy,
                    configuration = configuration,
                    fieldProviders = fieldProviders,
                    dateProvider = dateProvider,
                    apiUrl = apiUrl,
                    bridge = bridge,
                    context = context,
                    startResult = startResult,
                )
            }
        }

        /**
         * The Id for the current ongoing session.
         *
         * Returns `null` until the Capture SDK has finished starting successfully.
         * To obtain a guaranteed non-null value, use the [ILogger] instance delivered
         * via [CaptureResult.Success] to the `startResult` callback passed to [start].
         */
        @JvmStatic
        val sessionId: String?
            get() = logger()?.takeUnless { it is PreInitInMemoryLogger }?.sessionId

        /**
         * The URL for the current ongoing session.
         *
         * Returns `null` until the Capture SDK has finished starting successfully.
         * To obtain a guaranteed non-null value, use the [ILogger] instance delivered
         * via [CaptureResult.Success] to the `startResult` callback passed to [start].
         */
        @JvmStatic
        val sessionUrl: String?
            get() = logger()?.takeUnless { it is PreInitInMemoryLogger }?.sessionUrl

        /**
         * A canonical identifier for a device that remains consistent as long as an application
         * is not reinstalled.
         *
         * The value of this property is different for apps from the same vendor running on
         * the same device.
         *
         * Returns `null` until the Capture SDK has finished starting successfully.
         * To obtain a guaranteed non-null value, use the [ILogger] instance delivered
         * via [CaptureResult.Success] to the `startResult` callback passed to [start].
         */
        @JvmStatic
        val deviceId: String?
            get() = logger()?.takeUnless { it is PreInitInMemoryLogger }?.deviceId

        /**
         * Whether workflow-controlled tracing is currently active for this session.
         *
         * Returns `null` until the Capture SDK has finished starting successfully.
         * To obtain a guaranteed non-null value, use the [ILogger] instance delivered
         * via [CaptureResult.Success] to the `startResult` callback passed to [start].
         */
        @JvmStatic
        val isTracingActive: Boolean?
            get() = logger()?.isTracingActive

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
                mainThreadHandler.run { completion(CaptureResult.Failure(SdkNotStartedError)) }
            }
        }

        /**
         * Returns a snapshot of the previous app run status, or `null` if not available.
         *
         * Must be called after [start]. Returns `null` if the logger is not initialized.
         *
         */
        @JvmStatic
        @ExperimentalBitdriftApi
        fun getPreviousRunInfo(): PreviousRunInfo? = (logger() as? LoggerImpl)?.getPreviousRunInfo()

        /**
         * Returns a point-in-time snapshot of the SDK's operational status.
         */
        @JvmStatic
        fun getSdkStatus(): SdkStatus =
            (logger() as? LoggerImpl)?.getSdkStatus()
                ?: SdkStatus(InitializationState.NOT_STARTED, null, null)

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
         * Records a feature flag exposure with a variant. Use this method to track when
         * a user is exposed to a specific feature flag variant in your application. The exposure
         * is recorded with a timestamp and tracked for the duration of the process.
         *
         * @param name the name of the flag being exposed.
         * @param variant the variant of the flag being exposed.
         */
        @JvmStatic
        @ExperimentalBitdriftApi
        fun setFeatureFlagExposure(
            name: String,
            variant: String,
        ) {
            logger()?.setFeatureFlagExposure(name, variant)
        }

        /**
         * Sets an entity identifier for backend correlation with device identifier.
         * The value is hashed for storage and the exact value is never persisted.
         *
         * @param entityId entity identifier
         */
        @JvmStatic
        fun setEntityId(entityId: String) {
            logger()?.setEntityId(entityId)
        }

        /**
         * Clears the current entity identifier used for backend correlation with the device
         * identifier.
         */
        @JvmStatic
        fun clearEntityId() {
            logger()?.clearEntityId()
        }

        /**
         * Records a feature flag exposure with a boolean variant. Use this method to track when
         * a user is exposed to a specific feature flag variant in your application. The exposure
         * is recorded with a timestamp and tracked for the duration of the process.
         *
         * @param name the name of the flag being exposed
         * @param variant the boolean variant of the flag being exposed
         */
        @JvmStatic
        @ExperimentalBitdriftApi
        fun setFeatureFlagExposure(
            name: String,
            variant: Boolean,
        ) {
            logger()?.setFeatureFlagExposure(name, variant)
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
         * Reports a StrictMode violation detected by Android's StrictMode policies.
         * This method should be called from StrictMode violation listeners to capture
         * policy violations for observability.
         *
         * @param violation the StrictMode violation that was detected
         */
        @RequiresApi(Build.VERSION_CODES.P)
        @JvmStatic
        internal fun reportStrictModeViolation(violation: Violation) {
            (logger() as? LoggerImpl)
                ?.getIssueProcessor()
                ?.processStrictModeViolation(
                    violation = violation,
                )
        }

        /**
         * Internal API which allows to persist JavaScript error reports.
         *
         * @param errorName The main readable error name
         * @param message The detailed JavaScript error message
         * @param stack Raw stacktrace
         * @param isFatalIssue Indicates if this is a fatal JSError issue
         * @param engine Engine type (e.g. hermes/JSC)
         * @param debugId Debug id that will be used for de-minification
         * @param sdkVersion bitdrift's React Native SDK version(e.g 8.1)
         */
        @JvmStatic
        internal fun persistJavaScriptReport(
            errorName: String,
            message: String,
            stack: String,
            isFatalIssue: Boolean,
            engine: String,
            debugId: String,
            sdkVersion: String,
        ) {
            (logger() as? LoggerImpl)?.getIssueProcessor()?.processJavaScriptReport(
                errorName,
                message,
                stack,
                isFatalIssue,
                engine,
                debugId,
                sdkVersion,
            )
        }

        /**
         * Used for testing purposes.
         */
        internal fun resetShared() {
            default.set(LoggerState.NotStarted)
        }

        private fun dispatchStartResult(
            executor: Executor?,
            startResult: ((CaptureResult<ILogger>) -> Unit)?,
            result: CaptureResult<ILogger>,
        ) {
            if (executor != null) {
                executor.execute {
                    startResult.invokeCatchingOrThrowOnDebug(result)
                }
            } else {
                startResult.invokeCatchingOrThrowOnDebug(result)
            }
        }

        private fun hasInvalidContext(context: Context? = null) = context == null && !ContextHolder.isInitialized
    }

    /**
     * Usage: adb shell setprop debug.bitdrift.internal_rust_log debug
     * Sets up the internal logging filter for the rust library. This is done by reading a system
     * property and propagating it as an environment variable within the same process.
     * It swallows any failures and sets default to "info".
     *
     * This supports normal rust log filters, so for example so look at trace logs for only bd
     * crates to:
     * adb shell setprop debug.bitdrift.internal_rust_log info,bd=trace
     */
    @Suppress("SpreadOperator")
    @SuppressLint("PrivateApi")
    private fun setUpInternalLogging(context: Context) {
        if (BuildTypeChecker.isDebuggable(context)) {
            val defaultRustLog = "info"
            runCatching {
                // TODO(murki): Alternatively we could use JVM -D arg to pass properties
                //  that can be read via System.getProperty() but that's less Android-idiomatic
                // We follow the firebase approach https://firebase.google.com/docs/analytics/debugview#android
                // We call the internal API android.os.SystemProperties.get(key, default) using reflection
                Class
                    .forName("android.os.SystemProperties")
                    .getMethod("get", *arrayOf(String::class.java, String::class.java))
                    .invoke(null, *arrayOf("debug.bitdrift.internal_rust_log", defaultRustLog)) as? String
            }.getOrNull().let {
                val internalRustLog = it ?: defaultRustLog
                runCatching {
                    Os.setenv("RUST_LOG", internalRustLog, true)
                }
            }
        }
    }

    private fun initSdk(
        apiKey: String,
        sessionStrategy: SessionStrategy,
        configuration: Configuration,
        fieldProviders: List<FieldProvider>,
        dateProvider: DateProvider? = null,
        apiUrl: HttpUrl,
        bridge: IBridge,
        context: Context?,
        startResult: ((CaptureResult<ILogger>) -> Unit)? = null,
    ) {
        try {
            val startSdkTimer = TimeSource.Monotonic.markNow()

            if (!ContextHolder.isInitialized && context != null) {
                // Make sure the global context is set if no Initializer was used
                ContextHolder.APP_CONTEXT = context.applicationContext
            }

            val appContext = ContextHolder.APP_CONTEXT

            // This needs to happen before loading the native library as we set
            // up logging during library initialization.
            setUpInternalLogging(appContext)

            val nativeLoadDuration =
                measureTime {
                    CaptureJniLibrary.load()
                }

            val (loggerImpl, loggerImplBuildDuration) =
                measureTimedValue {
                    LoggerImpl(
                        apiKey = apiKey,
                        apiUrl = apiUrl,
                        context = appContext,
                        fieldProviders = fieldProviders,
                        dateProvider = dateProvider ?: SystemDateProvider(),
                        configuration = configuration,
                        sessionStrategy = sessionStrategy,
                        bridge = bridge,
                    )
                }

            default.set(LoggerState.Started(loggerImpl))

            preInitInMemoryLoggerRef.getAndSet(null)?.let { preInitLogger ->
                preInitLogger.flushToNative(loggerImpl)
                preInitLogger.clear()
            }

            // Must be initialized right after the logger state is set to avoid a null
            // Capture.logger() reference when onBeforeSend callbacks are triggered.
            loggerImpl.initIssueReporter()

            val sdkConfiguredDuration =
                SdkConfiguredDuration(
                    wholeStartDuration = startSdkTimer.elapsedNow(),
                    nativeLoadDuration = nativeLoadDuration,
                    loggerImplBuildDuration = loggerImplBuildDuration,
                )

            loggerImpl.writeSdkStartLog(
                appContext = appContext,
                sdkConfiguredDuration = sdkConfiguredDuration,
                captureStartThread = Thread.currentThread().name,
            )

            startResult.invokeCatchingOrThrowOnDebug(CaptureResult.Success(loggerImpl))
        } catch (throwable: Throwable) {
            preInitInMemoryLoggerRef.getAndSet(null)?.clear()

            if (throwable.shouldReThrowOnDebugBuild()) throw throwable
            val errorDetails = "Failed to start Capture: ${throwable.message}"
            Log.w(LOG_TAG, errorDetails, throwable)
            default.set(LoggerState.StartFailure)
            startResult.invokeCatchingOrThrowOnDebug(CaptureResult.Failure(SdkStartFailure(errorDetails, throwable)))
        }
    }

    private fun Throwable.shouldReThrowOnDebugBuild(): Boolean = this is DebugCustomerCallbackException
}
