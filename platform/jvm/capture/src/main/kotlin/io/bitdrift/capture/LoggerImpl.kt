// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.system.Os
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
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
import io.bitdrift.capture.events.lifecycle.EventSubscriber
import io.bitdrift.capture.events.performance.BatteryMonitor
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import io.bitdrift.capture.events.performance.JankStatsMonitor
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
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.IFatalIssueReporter
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.BuildTypeChecker
import io.bitdrift.capture.utils.SdkDirectory
import okhttp3.HttpUrl
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit

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
    context: Context,
    private val clientAttributes: ClientAttributes =
        ClientAttributes(
            context,
            ProcessLifecycleOwner.get(),
        ),
    preferences: IPreferences = Preferences(context),
    private val apiClient: OkHttpApiClient = OkHttpApiClient(apiUrl, apiKey),
    private var deviceCodeService: DeviceCodeService = DeviceCodeService(apiClient),
    activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager,
    bridge: IBridge = CaptureJniLibrary,
    private val eventListenerDispatcher: CaptureDispatchers.CommonBackground = CaptureDispatchers.CommonBackground,
    windowManager: IWindowManager = WindowManager(errorHandler),
    private val fatalIssueReporter: IFatalIssueReporter? =
        if (configuration.enableFatalIssueReporting) {
            FatalIssueReporter(configuration.enableNativeCrashReporting)
        } else {
            null
        },
) : ILogger,
    ICompletedReportsProcessor {
    private val metadataProvider: MetadataProvider
    private val batteryMonitor = BatteryMonitor(context)
    private val powerMonitor = PowerMonitor(context)
    private val diskUsageMonitor: DiskUsageMonitor
    private val memoryMetricsProvider: MemoryMetricsProvider
    private val appExitLogger: AppExitLogger
    private val runtime: JniRuntime
    private var jankStatsMonitor: JankStatsMonitor? = null

    // we can assume a properly formatted api url is being used, so we can follow the same pattern
    // making sure we only replace the first occurrence
    private val sessionUrlBase: HttpUrl

    private val resourceUtilizationTarget: ResourceUtilizationTarget
    private val eventSubscriber = EventSubscriber()

    private val sessionReplayTarget: SessionReplayTarget?

    @VisibleForTesting
    internal val loggerId: LoggerId

    init {
        setUpInternalLogging(context)

        this.sessionUrlBase =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(apiUrl.host.replaceFirst("api.", "timeline."))
                .addQueryParameter("utm_source", "sdk")
                .build()

        val networkAttributes = NetworkAttributes(context)
        val deviceAttributes = DeviceAttributes(context)

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
                errorHandler = errorHandler,
                customFieldProviders = fieldProviders,
            )

        val network =
            OkHttpNetwork(
                apiBaseUrl = apiUrl,
            )

        val sdkDirectory = SdkDirectory.getPath(context)

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
        memoryMetricsProvider = MemoryMetricsProvider(activityManager)

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

        this.sessionReplayTarget =
            SessionReplayTarget(
                configuration = configuration.sessionReplayConfiguration,
                errorHandler,
                context,
                logger = this,
                windowManager = windowManager,
            )

        val loggerId =
            bridge.createLogger(
                sdkDirectory,
                apiKey,
                sessionStrategy.createSessionStrategyConfiguration { appExitSaveCurrentSessionId(it) },
                metadataProvider,
                // TODO(Augustyniak): Pass `resourceUtilizationTarget`, `sessionReplayTarget`,
                //  and `eventSubscriber` as part of `startLogger` method call instead.
                // Pass the event listener target here and finish setting up
                // before the logger is actually started.
                resourceUtilizationTarget,
                sessionReplayTarget,
                // Pass the event listener target here and finish setting up
                // before the logger is actually started.
                eventSubscriber,
                clientAttributes.appId,
                clientAttributes.appVersion,
                deviceAttributes.model(),
                network,
                preferences,
                localErrorReporter,
                configuration.sleepMode == SleepMode.ACTIVE,
            )

        check(loggerId != -1L) { "initialization of the rust logger failed" }

        this.loggerId = loggerId

        runtime = JniRuntime(this.loggerId)
        sessionReplayTarget.runtime = runtime
        diskUsageMonitor.runtime = runtime
        memoryMetricsProvider.runtime = runtime

        eventSubscriber.add(
            AppLifecycleListenerLogger(
                this,
                ProcessLifecycleOwner.get(),
                activityManager,
                runtime,
                eventListenerDispatcher.executorService,
            ),
        )

        eventSubscriber.add(
            DeviceStateListenerLogger(
                this,
                context,
                batteryMonitor,
                powerMonitor,
                runtime,
                eventListenerDispatcher.executorService,
            ),
        )

        eventSubscriber.add(
            AppUpdateListenerLogger(
                this,
                clientAttributes,
                context,
                runtime,
                eventListenerDispatcher.executorService,
            ),
        )

        addJankStatsMonitorTarget(windowManager, context)

        appExitLogger =
            AppExitLogger(
                logger = this,
                activityManager,
                runtime,
                errorHandler,
                memoryMetricsProvider = memoryMetricsProvider,
                isFatalIssueReporterEnabled = configuration.enableFatalIssueReporting,
            )

        // Install the app exit logger before the Capture logger is started to ensure
        // that logs emitted during the installation are the first logs emitted by the
        // Capture logger.
        appExitLogger.installAppExitLogger()

        CaptureJniLibrary.startLogger(this.loggerId)

        // fatal issue reporter needs to be initialized after appExitLogger and the jniLogger
        fatalIssueReporter?.initBuiltInMode(context, clientAttributes, this)
    }

    override fun processCrashReports() {
        CaptureJniLibrary.processCrashReports(this.loggerId)
    }

    override fun onReportProcessingError(
        message: String,
        throwable: Throwable,
    ) {
        errorHandler.handleError(message, throwable)
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
        } ?: completion(CaptureResult.Failure(SdkNotStartedError))
    }

    @SuppressLint("NewApi")
    private fun appExitSaveCurrentSessionId(sessionId: String? = null) {
        appExitLogger.saveCurrentSessionId(sessionId)
    }

    override fun logAppLaunchTTI(duration: Duration) {
        CaptureJniLibrary.writeAppLaunchTTILog(this.loggerId, duration.toDouble(DurationUnit.SECONDS))
    }

    override fun logScreenView(screenName: String) {
        jankStatsMonitor?.trackScreenNameChanged(screenName)
        CaptureJniLibrary.writeScreenViewLog(this.loggerId, screenName)
    }

    override fun startSpan(
        name: String,
        level: LogLevel,
        fields: Map<String, String>?,
        startTimeMs: Long?,
        parentSpanId: UUID?,
    ): Span = Span(this, name, level, fields, startTimeMs, parentSpanId)

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

    override fun setSleepMode(sleepMode: SleepMode) {
        CaptureJniLibrary.setSleepModeEnabled(this.loggerId, sleepMode == SleepMode.ACTIVE)
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
            val expectedPreviousProcessSessionId =
                when (attributesOverrides) {
                    is LogAttributesOverrides.SessionID -> attributesOverrides.expectedPreviousProcessSessionId
                    is LogAttributesOverrides.OccurredAt -> null
                    else -> null
                }
            val occurredAtTimestampMs: Long =
                when (attributesOverrides) {
                    is LogAttributesOverrides.SessionID -> attributesOverrides.occurredAtTimestampMs
                    is LogAttributesOverrides.OccurredAt -> attributesOverrides.occurredAtTimestampMs
                    else -> 0
                }

            CaptureJniLibrary.writeLog(
                this.loggerId,
                type.value,
                level.value,
                message(),
                fields ?: mapOf(),
                matchingFields ?: mapOf(),
                expectedPreviousProcessSessionId,
                occurredAtTimestampMs,
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
    ): InternalFieldsMap {
        // Maintainer note: keep initialCapacity in sync with the code adding fields to the map.
        val initialCapacity = (fields?.size ?: 0) + (throwable?.let { 2 } ?: 0)
        if (initialCapacity == 0) {
            // If throwable is null AND fields is either null or empty, no need to create a HashMap.
            return emptyMap()
        }
        // Create a hashmap of the exact target size and with the right final value type, instead
        // of creating a temporary map and then converting it with Map.toFields()
        val extractedFields = HashMap<String, FieldValue>(initialCapacity)
        fields?.let {
            for ((key, value) in it) {
                // Java interop: clients could have passed in null keys or values.
                @Suppress("SENSELESS_COMPARISON")
                if (key != null && value != null) {
                    extractedFields[key] = value.toFieldValue()
                }
            }
        }
        throwable?.let {
            extractedFields["_error"] =
                it.javaClass.name
                    .orEmpty()
                    .toFieldValue()
            extractedFields["_error_details"] = it.message.orEmpty().toFieldValue()
        }
        return extractedFields
    }

    internal fun flush(blocking: Boolean) {
        CaptureJniLibrary.flush(this.loggerId, blocking)
    }

    @Suppress("UnusedPrivateMember")
    private fun stopLoggingDefaultEvents() {
        appExitLogger.uninstallAppExitLogger()
    }

    internal data class SdkConfiguredDuration(
        val wholeStartDuration: Duration,
        val nativeLoadDuration: Duration,
        val loggerImplBuildDuration: Duration,
    )

    /**
     * Emits the SDKConfigured event with details about its duration, caller thread, etc
     */
    internal fun writeSdkStartLog(
        appContext: Context,
        sdkConfiguredDuration: SdkConfiguredDuration,
        captureStartThread: String,
    ) {
        eventListenerDispatcher.executorService.execute {
            val installationSource =
                clientAttributes
                    .getInstallationSource(appContext, errorHandler)
                    .toFieldValue()

            val sdkStartFields =
                buildMap {
                    put("_app_installation_source", installationSource)
                    put("_capture_start_thread", captureStartThread.toFieldValue())
                    put(
                        "_native_load_duration_ms",
                        sdkConfiguredDuration.nativeLoadDuration.toFieldValue(DurationUnit.MILLISECONDS),
                    )
                    put("_logger_build_duration_ms", sdkConfiguredDuration.loggerImplBuildDuration.toFieldValue(DurationUnit.MILLISECONDS))
                    fatalIssueReporter?.let {
                        putAll(it.getLogStatusFieldsMap())
                    }
                }

            CaptureJniLibrary.writeSDKStartLog(
                this.loggerId,
                sdkStartFields,
                sdkConfiguredDuration.wholeStartDuration.toDouble(DurationUnit.SECONDS),
            )
        }
    }

    /**
     * Usage: adb shell setprop debug.bitdrift.internal_log_level debug
     * Sets up the internal logging level for the rust library. This is done by reading a system
     * property and propagating it as an environment variable within the same process.
     * It swallows any failures and sets default to "info".
     */
    @Suppress("SpreadOperator")
    @SuppressLint("PrivateApi")
    private fun setUpInternalLogging(context: Context) {
        if (BuildTypeChecker.isDebuggable(context)) {
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

    private fun addJankStatsMonitorTarget(
        windowManager: IWindowManager,
        context: Context,
    ) {
        if (context is Application) {
            jankStatsMonitor =
                JankStatsMonitor(
                    context,
                    this,
                    ProcessLifecycleOwner.get(),
                    runtime,
                    windowManager,
                    errorHandler,
                )
            jankStatsMonitor?.let {
                eventSubscriber.add(it)
            }
        } else {
            errorHandler.handleError("Couldn't start JankStatsMonitor. Invalid application provided")
        }
    }
}

internal sealed class LogAttributesOverrides {
    data class SessionID(
        val expectedPreviousProcessSessionId: String,
        val occurredAtTimestampMs: Long,
    ) : LogAttributesOverrides()

    data class OccurredAt(
        val occurredAtTimestampMs: Long,
    ) : LogAttributesOverrides()
}
