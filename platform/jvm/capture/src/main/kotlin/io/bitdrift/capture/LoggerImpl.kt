// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.system.Os
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.metrics.performance.PerformanceMetricsState
import com.github.michaelbull.result.Err
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.DeviceAttributes
import io.bitdrift.capture.attributes.NetworkAttributes
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.common.WindowManager
import io.bitdrift.capture.error.ErrorReporterService
import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.events.AppUpdateListenerLogger
import io.bitdrift.capture.events.SessionReplayTarget
import io.bitdrift.capture.events.common.PowerMonitor
import io.bitdrift.capture.events.device.DeviceStateListenerLogger
import io.bitdrift.capture.events.lifecycle.AppExitLogger
import io.bitdrift.capture.events.lifecycle.AppLifecycleListenerLogger
import io.bitdrift.capture.events.lifecycle.EventsListenerTarget
import io.bitdrift.capture.events.performance.AppMemoryPressureListenerLogger
import io.bitdrift.capture.events.performance.BatteryMonitor
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import io.bitdrift.capture.events.performance.JankStatsMonitor
import io.bitdrift.capture.events.performance.JankStatsMonitor.Companion.SCREEN_NAME_KEY
import io.bitdrift.capture.events.performance.MemoryMetricsProvider
import io.bitdrift.capture.events.performance.ResourceUtilizationTarget
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import io.bitdrift.capture.network.okhttp.OkHttpNetwork
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.MetadataProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

typealias LoggerId = Long
internal typealias InternalFieldsList = List<Field>
internal typealias InternalFieldsMap = Map<String, FieldValue>

internal class LoggerImpl(
    apiKey: String,
    apiUrl: HttpUrl,
    errorReporter: IErrorReporter? = null,
    configuration: Configuration,
    fieldProviders: List<FieldProvider>,
    dateProvider: DateProvider,
    private val errorHandler: ErrorHandler = ErrorHandler(),
    sessionStrategy: SessionStrategy,
    context: Context = ContextHolder.APP_CONTEXT,
    clientAttributes: ClientAttributes =
        ClientAttributes(
            context,
            ProcessLifecycleOwner.get(),
        ),
    preferences: IPreferences = Preferences(context),
    private val apiClient: OkHttpApiClient = OkHttpApiClient(apiUrl, apiKey),
    private var deviceCodeService: DeviceCodeService = DeviceCodeService(apiClient),
    private val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager,
    private val bridge: IBridge = CaptureJniLibrary,
    private val eventListenerDispatcher: CaptureDispatchers.CommonBackground = CaptureDispatchers.CommonBackground,
    private val windowManager: IWindowManager = WindowManager(errorHandler),
) : ILogger {
    private val metadataProvider: MetadataProvider
    private val memoryMetricsProvider = MemoryMetricsProvider(context)
    private val batteryMonitor = BatteryMonitor(context)
    private val powerMonitor = PowerMonitor(context)
    private val diskUsageMonitor: DiskUsageMonitor
    private val appExitLogger: AppExitLogger
    private val runtime: JniRuntime

    // we can assume a properly formatted api url is being used, so we can follow the same pattern
    // making sure we only replace the first occurrence
    private val sessionUrlBase: HttpUrl

    private val resourceUtilizationTarget: ResourceUtilizationTarget
    private val eventsListenerTarget = EventsListenerTarget()

    private val sessionReplayTarget: SessionReplayTarget?

    @VisibleForTesting
    internal val loggerId: LoggerId

    init {
        val duration =
            measureTime {
                setUpInternalLogging()

                CaptureJniLibrary.load()

                this.sessionUrlBase =
                    HttpUrl
                        .Builder()
                        .scheme("https")
                        .host(apiUrl.host.replaceFirst("api.", "timeline."))
                        .addQueryParameter("utm_source", "sdk")
                        .build()

                val networkAttributes = NetworkAttributes(ContextHolder.APP_CONTEXT)
                val deviceAttributes = DeviceAttributes(ContextHolder.APP_CONTEXT)

                metadataProvider =
                    MetadataProvider(
                        dateProvider = dateProvider,
                        // order of providers matters in here, the earlier in the list the higher their priority in
                        // case of key conflicts.
                        ootbFieldProviders =
                            listOf(
                                clientAttributes,
                                networkAttributes,
                                deviceAttributes,
                            ),
                        customFieldProviders = fieldProviders,
                    )

                val network =
                    OkHttpNetwork(
                        apiBaseUrl = apiUrl,
                    )

                val sdkDirectory = getSdkDirectoryPath(context)

                val localErrorReporter =
                    errorReporter ?: ErrorReporterService(
                        listOf(clientAttributes),
                        apiClient,
                    )

                diskUsageMonitor =
                    DiskUsageMonitor(
                        preferences,
                        context,
                    )

                resourceUtilizationTarget =
                    ResourceUtilizationTarget(
                        memoryMetricsProvider,
                        batteryMonitor,
                        powerMonitor,
                        diskUsageMonitor,
                        errorHandler,
                        this,
                        eventListenerDispatcher.executorService,
                    )

                val sessionReplayTarget =
                    SessionReplayTarget(
                        configuration = configuration.sessionReplayConfiguration,
                        errorHandler,
                        context,
                        logger = this,
                        windowManager = windowManager,
                    )

                this.sessionReplayTarget = sessionReplayTarget

                val loggerId =
                    bridge.createLogger(
                        sdkDirectory,
                        apiKey,
                        sessionStrategy.createSessionStrategyConfiguration { appExitSaveCurrentSessionId(it) },
                        metadataProvider,
                        // TODO(Augustyniak): Pass `resourceUtilizationTarget`, `sessionReplayTarget`,
                        //  and `eventsListenerTarget` as part of `startLogger` method call instead.
                        // Pass the event listener target here and finish setting up
                        // before the logger is actually started.
                        resourceUtilizationTarget,
                        sessionReplayTarget,
                        // Pass the event listener target here and finish setting up
                        // before the logger is actually started.
                        eventsListenerTarget,
                        clientAttributes.appId,
                        clientAttributes.appVersion,
                        deviceAttributes.model(),
                        network,
                        preferences,
                        localErrorReporter,
                    )

                check(loggerId != -1L) { "initialization of the rust logger failed" }

                this.loggerId = loggerId

                runtime = JniRuntime(this.loggerId)
                sessionReplayTarget.runtime = runtime
                diskUsageMonitor.runtime = runtime

                eventsListenerTarget.add(
                    AppLifecycleListenerLogger(
                        this,
                        ProcessLifecycleOwner.get(),
                        activityManager,
                        runtime,
                        eventListenerDispatcher.executorService,
                    ),
                )

                eventsListenerTarget.add(
                    DeviceStateListenerLogger(
                        this,
                        context,
                        batteryMonitor,
                        powerMonitor,
                        runtime,
                        eventListenerDispatcher.executorService,
                    ),
                )

                eventsListenerTarget.add(
                    AppMemoryPressureListenerLogger(
                        this,
                        context,
                        memoryMetricsProvider,
                        runtime,
                        eventListenerDispatcher.executorService,
                    ),
                )

                eventsListenerTarget.add(
                    AppUpdateListenerLogger(
                        this,
                        clientAttributes,
                        context,
                        runtime,
                        eventListenerDispatcher.executorService,
                    ),
                )

                eventsListenerTarget.add(
                    JankStatsMonitor(
                        this,
                        ProcessLifecycleOwner.get(),
                        runtime,
                        windowManager,
                        errorHandler,
                    ),
                )

                appExitLogger =
                    AppExitLogger(
                        logger = this,
                        activityManager,
                        runtime,
                        errorHandler,
                        memoryMetricsProvider = memoryMetricsProvider,
                    )

                // Install the app exit logger before the Capture logger is started to ensure
                // that logs emitted during the installation are the first logs emitted by the
                // Capture logger.
                appExitLogger.installAppExitLogger()

                CaptureJniLibrary.startLogger(this.loggerId)
            }

        CaptureJniLibrary.writeSDKStartLog(
            this.loggerId,
            mapOf(),
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    override val sessionId: String
        get() = CaptureJniLibrary.getSessionId(this.loggerId) ?: "unknown"

    override val sessionUrl: String
        get() =
            sessionUrlBase
                .newBuilder()
                .addPathSegment("s")
                .addPathSegment(sessionId)
                .build()
                .toString()

    override val deviceId: String
        get() = CaptureJniLibrary.getDeviceId(this.loggerId) ?: "unknown"

    override fun startNewSession() {
        CaptureJniLibrary.startNewSession(this.loggerId)
        appExitSaveCurrentSessionId()
    }

    override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) {
        CaptureJniLibrary.getDeviceId(this.loggerId)?.let { deviceId ->
            /**
             *  Access the `deviceId` when it is needed for creating the device code, rather than
             *  at Logger's initialization time. Accessing it later almost guarantees that the
             *  `deviceId` has been read and cached on the Tokio run-loop, making it a relatively
             *  cheap operation. This approach avoids the heavy operation of reading from
             *  `SharedPreferences`.
             */
            deviceCodeService.createTemporaryDeviceCode(deviceId, completion)
        } ?: completion(Err(SdkNotStartedError))
    }

    private fun appExitSaveCurrentSessionId(sessionId: String? = null) {
        appExitLogger.saveCurrentSessionId(sessionId)
    }

    override fun logAppLaunchTTI(duration: Duration) {
        CaptureJniLibrary.writeAppLaunchTTILog(this.loggerId, duration.toDouble(DurationUnit.SECONDS))
    }

    override fun logScreenView(screenName: String) {
        if (runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
            // Use metrics state holder to pass the screen name to JankStatsMonitor
            windowManager.getAllRootViews().firstOrNull()?.let { rootView ->
                PerformanceMetricsState.getHolderForHierarchy(rootView).state?.putState(SCREEN_NAME_KEY, screenName)
            }
        }

        CaptureJniLibrary.writeScreenViewLog(this.loggerId, screenName)
    }

    override fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>?,
    ): Span = Span(this, name, level, fields)

    override fun log(httpRequestInfo: HttpRequestInfo) {
        log(
            LogType.SPAN,
            LogLevel.DEBUG,
            httpRequestInfo.fields,
            httpRequestInfo.matchingFields,
        ) { httpRequestInfo.name }
    }

    override fun log(httpResponseInfo: HttpResponseInfo) {
        log(
            LogType.SPAN,
            LogLevel.DEBUG,
            httpResponseInfo.fields,
            httpResponseInfo.matchingFields,
        ) { httpResponseInfo.name }
    }

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    ) {
        log(
            LogType.NORMAL,
            level,
            extractFields(fields, throwable),
            null,
            null,
            false,
            message,
        )
    }

    override fun addField(
        key: String,
        value: String,
    ) {
        CaptureJniLibrary.addLogField(this.loggerId, key, value)
    }

    override fun removeField(key: String) {
        CaptureJniLibrary.removeLogField(this.loggerId, key)
    }

    @JvmName("logFields")
    @Suppress("TooGenericExceptionCaught")
    internal fun log(
        type: LogType,
        level: LogLevel,
        fields: InternalFieldsMap? = null,
        matchingFields: InternalFieldsMap? = null,
        attributesOverrides: LogAttributesOverrides? = null,
        blocking: Boolean = false,
        message: () -> String,
    ) {
        if (type == LogType.INTERNALSDK && !runtime.isEnabled(RuntimeFeature.INTERNAL_LOGS)) {
            return
        }
        try {
            CaptureJniLibrary.writeLog(
                this.loggerId,
                type.value,
                level.value,
                message(),
                fields ?: mapOf(),
                matchingFields ?: mapOf(),
                attributesOverrides?.expectedPreviousProcessSessionId,
                attributesOverrides?.occurredAtTimestampMs ?: 0,
                blocking,
            )
        } catch (e: Throwable) {
            errorHandler.handleError("write log", e)
        }
    }

    internal fun logSessionReplayScreen(
        fields: Map<String, FieldValue>,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeSessionReplayScreenLog(
            this.loggerId,
            fields,
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    internal fun logSessionReplayScreenshot(
        fields: Map<String, FieldValue>,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeSessionReplayScreenshotLog(
            this.loggerId,
            fields,
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    internal fun logResourceUtilization(
        fields: Map<String, String>,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeResourceUtilizationLog(
            this.loggerId,
            fields.toFields(),
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    internal fun shouldLogAppUpdate(
        appVersion: String,
        appVersionCode: Long,
    ): Boolean = CaptureJniLibrary.shouldWriteAppUpdateLog(this.loggerId, appVersion, appVersionCode)

    internal fun logAppUpdate(
        appVersion: String,
        appVersionCode: Long,
        appSizeBytes: Long,
        durationS: Double,
    ) {
        CaptureJniLibrary.writeAppUpdateLog(this.loggerId, appVersion, appVersionCode, appSizeBytes, durationS)
    }

    internal fun extractFields(
        fields: Map<String, String>?,
        throwable: Throwable?,
    ): InternalFieldsMap? =
        buildMap {
            fields?.let {
                putAll(it)
            }
            throwable?.let {
                put("_error", it.javaClass.name.orEmpty())
                put("_error_details", it.message.orEmpty())
            }
        }.toFields()

    internal fun flush(blocking: Boolean) {
        CaptureJniLibrary.flush(this.loggerId, blocking)
    }

    private fun getSdkDirectoryPath(context: Context): String {
        val directory = context.applicationContext.filesDir
        val sdkDirectory = File(directory.absolutePath, "bitdrift_capture")
        return sdkDirectory.absolutePath
    }

    @Suppress("UnusedPrivateMember")
    private fun stopLoggingDefaultEvents() {
        appExitLogger.uninstallAppExitLogger()
    }

    /**
     * Usage: adb shell setprop debug.bitdrift.internal_log_level debug
     * Sets up the internal logging level for the rust library. This is done by reading a system
     * property and propagating it as an environment variable within the same process.
     * It swallows any failures and sets default to "info".
     */
    @Suppress("SpreadOperator")
    @SuppressLint("PrivateApi")
    private fun setUpInternalLogging() {
        // Determine if app is debuggable using this bitwise operation
        if (ContextHolder.APP_CONTEXT.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val defaultLevel = "info"
            runCatching {
                // TODO(murki): Alternatively we could use JVM -D arg to pass properties
                //  that can be read via System.getProperty() but that's less Android-idiomatic
                // We follow the firebase approach https://firebase.google.com/docs/analytics/debugview#android
                // We call the internal API android.os.SystemProperties.get(key, default) using reflection
                Class
                    .forName("android.os.SystemProperties")
                    .getMethod("get", *arrayOf(String::class.java, String::class.java))
                    .invoke(null, *arrayOf("debug.bitdrift.internal_log_level", defaultLevel)) as? String
            }.getOrNull().let {
                val internalLogLevel = it ?: defaultLevel
                runCatching {
                    Os.setenv("RUST_LOG", internalLogLevel, true)
                }
            }
        }
    }
}

internal data class LogAttributesOverrides(
    val expectedPreviousProcessSessionId: String,
    val occurredAtTimestampMs: Long,
)
