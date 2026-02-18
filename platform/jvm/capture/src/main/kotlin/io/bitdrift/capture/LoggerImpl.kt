// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import io.bitdrift.capture.attributes.ClientAttributes
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
import io.bitdrift.capture.events.performance.BatteryMonitor
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import io.bitdrift.capture.events.performance.JankStatsMonitor
import io.bitdrift.capture.events.performance.MemoryMetricsProvider
import io.bitdrift.capture.events.performance.ResourceUtilizationTarget
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import io.bitdrift.capture.network.okhttp.OkHttpNetwork
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.MetadataProvider
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.providers.toLegacyJniFields
import io.bitdrift.capture.reports.IssueReporter
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.IIssueReporterProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.BuildTypeChecker
import io.bitdrift.capture.utils.SdkDirectory
import io.bitdrift.capture.webview.WebViewConfiguration
import io.bitdrift.capture.webview.toFields
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit

typealias LoggerId = Long

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
    sharedOkHttpClient: OkHttpClient = OkHttpClient(),
    private val apiClient: OkHttpApiClient = OkHttpApiClient(apiUrl, apiKey, client = sharedOkHttpClient),
    private var deviceCodeService: DeviceCodeService = DeviceCodeService(apiClient),
    activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager,
    bridge: IBridge = CaptureJniLibrary,
    private val eventListenerDispatcher: CaptureDispatchers.CommonBackground = CaptureDispatchers.CommonBackground,
    windowManager: IWindowManager = WindowManager(errorHandler),
) : IInternalLogger,
    ICompletedReportsProcessor {
    @OptIn(ExperimentalBitdriftApi::class)
    internal val webViewConfiguration: WebViewConfiguration? = configuration.webViewConfiguration

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
    private val eventsListenerTarget = EventsListenerTarget()

    private val sessionReplayTarget: ISessionReplayTarget

    private val issueReporter: IssueReporter? =
        if (configuration.enableFatalIssueReporting) {
            IssueReporter(internalLogger = this, dateProvider = dateProvider)
        } else {
            null
        }

    @VisibleForTesting
    internal val loggerId: LoggerId

    init {
        this.sessionUrlBase =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(apiUrl.host.replaceFirst("api.", "timeline."))
                .addQueryParameter("utm_source", "sdk")
                .build()

        val networkAttributes = NetworkAttributes(context)

        metadataProvider =
            MetadataProvider(
                dateProvider = dateProvider,
                // order of providers matters in here, the earlier in the list the higher their priority in
                // case of key conflicts.
                ootbFieldProviders =
                    listOf(
                        clientAttributes,
                        networkAttributes,
                    ),
                errorHandler = errorHandler,
                customFieldProviders = fieldProviders,
            )

        val network =
            OkHttpNetwork(
                apiBaseUrl = apiUrl,
                okHttpClient = sharedOkHttpClient,
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
                this,
                eventListenerDispatcher.executorService,
            )

        this.sessionReplayTarget =
            configuration.sessionReplayConfiguration?.let { sessionReplayConfiguration ->
                SessionReplayTarget(
                    configuration = sessionReplayConfiguration,
                    errorHandler,
                    context,
                    logger = this,
                    windowManager = windowManager,
                )
            } ?: NoopSessionReplayTarget()

        val loggerId =
            bridge.createLogger(
                sdkDirectory,
                apiKey,
                sessionStrategy.createSessionStrategyConfiguration(),
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
                clientAttributes.model,
                network,
                preferences,
                localErrorReporter,
                configuration.sleepMode == SleepMode.ENABLED,
            )

        check(loggerId != -1L) { "initialization of the rust logger failed" }

        this.loggerId = loggerId

        runtime = JniRuntime(this.loggerId)
        if (sessionReplayTarget is SessionReplayTarget) {
            sessionReplayTarget.runtime = runtime
        }
        diskUsageMonitor.runtime = runtime
        memoryMetricsProvider.runtime = runtime

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
                memoryMetricsProvider = memoryMetricsProvider,
                issueReporter = issueReporter,
            )

        // Install the app exit logger before the Capture logger is started to ensure
        // that logs emitted during the installation are the first logs emitted by the
        // Capture logger.
        appExitLogger.installAppExitLogger()

        CaptureJniLibrary.startLogger(this.loggerId)

        // issue reporter needs to be initialized after appExitLogger and the jniLogger
        issueReporter?.init(
            activityManager = activityManager,
            sdkDirectory = sdkDirectory,
            clientAttributes = clientAttributes,
            completedReportsProcessor = this,
        )

        startDebugOperationsAsNeeded(context)
    }

    override fun processIssueReports(reportProcessingSession: ReportProcessingSession) {
        CaptureJniLibrary.processIssueReports(this.loggerId, reportProcessingSession)
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
    ): Span = Span(this, name, level, fields?.toFields(), startTimeMs, parentSpanId)

    override fun log(httpRequestInfo: HttpRequestInfo) {
        logInternal(
            LogType.SPAN,
            LogLevel.DEBUG,
            httpRequestInfo.arrayFields,
            httpRequestInfo.matchingArrayFields,
        ) { httpRequestInfo.name }
    }

    override fun log(httpResponseInfo: HttpResponseInfo) {
        logInternal(
            LogType.SPAN,
            LogLevel.DEBUG,
            httpResponseInfo.arrayFields,
            httpResponseInfo.matchingArrayFields,
        ) { httpResponseInfo.name }
    }

    override fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    ) {
        logInternal(
            LogType.NORMAL,
            level,
            extractFields(fields, throwable),
            ArrayFields.EMPTY,
            null,
            false,
            message,
        )
    }

    override fun log(
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        message: () -> String,
    ) {
        logInternal(
            LogType.NORMAL,
            level,
            arrayFields,
            ArrayFields.EMPTY,
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

    override fun setFeatureFlagExposure(
        name: String,
        variant: String,
    ) {
        CaptureJniLibrary.setFeatureFlagExposure(this.loggerId, name, variant)
    }

    override fun setFeatureFlagExposure(
        name: String,
        variant: Boolean,
    ) {
        // TODO(snowp): We should make the internal state store expose a way to set the bool directly
        CaptureJniLibrary.setFeatureFlagExposure(this.loggerId, name, variant.toString())
    }

    override fun setSleepMode(sleepMode: SleepMode) {
        CaptureJniLibrary.setSleepModeEnabled(this.loggerId, sleepMode == SleepMode.ENABLED)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields,
        matchingArrayFields: ArrayFields,
        attributesOverrides: LogAttributesOverrides?,
        blocking: Boolean,
        message: () -> String,
    ) {
        if (type == LogType.INTERNALSDK && !runtime.isEnabled(RuntimeFeature.INTERNAL_LOGS)) {
            return
        }
        try {
            val previousRunSessionId =
                when (attributesOverrides) {
                    is LogAttributesOverrides.PreviousRunSessionId -> true
                    is LogAttributesOverrides.OccurredAt -> false
                    else -> false
                }
            val occurredAtTimestampMs: Long =
                when (attributesOverrides) {
                    is LogAttributesOverrides.PreviousRunSessionId -> attributesOverrides.occurredAtTimestampMs
                    is LogAttributesOverrides.OccurredAt -> attributesOverrides.occurredAtTimestampMs
                    else -> 0
                }

            CaptureJniLibrary.writeLog(
                this.loggerId,
                type.value,
                level.value,
                message(),
                arrayFields.keys,
                arrayFields.values,
                matchingArrayFields.keys,
                matchingArrayFields.values,
                previousRunSessionId,
                occurredAtTimestampMs,
                blocking,
            )
        } catch (e: Throwable) {
            errorHandler.handleError("write log", e)
        }
    }

    override fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        message: () -> String,
    ) {
        val throwableFields =
            if (throwable == null) {
                ArrayFields.EMPTY
            } else {
                ArrayFields(
                    arrayOf("_error", "_error_details"),
                    arrayOf(throwable.javaClass.name.orEmpty(), throwable.message.orEmpty()),
                )
            }
        logInternal(
            type,
            level,
            arrayFields = combineFields(arrayFields, throwableFields),
            matchingArrayFields = ArrayFields.EMPTY,
            attributesOverrides = null,
            blocking = false,
            message,
        )
    }

    override fun handleInternalError(
        detail: String,
        throwable: Throwable?,
    ) {
        errorHandler.handleError(detail, throwable)
    }

    override fun logSessionReplayScreen(
        fields: Array<Field>,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeSessionReplayScreenLog(
            this.loggerId,
            fields,
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    override fun logSessionReplayScreenshot(
        fields: Array<Field>,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeSessionReplayScreenshotLog(
            this.loggerId,
            fields,
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    override fun logResourceUtilization(
        arrayFields: ArrayFields,
        duration: Duration,
    ) {
        CaptureJniLibrary.writeResourceUtilizationLog(
            this.loggerId,
            arrayFields.toLegacyJniFields(),
            duration.toDouble(DurationUnit.SECONDS),
        )
    }

    override fun flush(blocking: Boolean) {
        CaptureJniLibrary.flush(this.loggerId, blocking)
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
    ): ArrayFields {
        val hasThrowable = throwable != null
        val fieldsSize = fields?.size ?: 0

        if (fieldsSize == 0 && !hasThrowable) {
            return ArrayFields.EMPTY
        }

        val throwableFieldCount = if (hasThrowable) 2 else 0
        val totalSize = fieldsSize + throwableFieldCount

        val keys = ArrayList<String>(totalSize)
        val values = ArrayList<String>(totalSize)

        fields?.forEach { (key, value) ->
            @Suppress("SENSELESS_COMPARISON")
            if (key != null && value != null) {
                keys.add(key)
                values.add(value)
            }
        }

        throwable?.let {
            keys.add("_error")
            values.add(it.javaClass.name.orEmpty())
            keys.add("_error_details")
            values.add(it.message.orEmpty())
        }

        return ArrayFields(
            keys.toTypedArray(),
            values.toTypedArray(),
        )
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
    @OptIn(ExperimentalBitdriftApi::class)
    internal fun writeSdkStartLog(
        appContext: Context,
        sdkConfiguredDuration: SdkConfiguredDuration,
        captureStartThread: String,
    ) {
        eventListenerDispatcher.executorService.execute {
            val installationSource =
                clientAttributes
                    .getInstallationSource(appContext, errorHandler)
            val isSessionReplayEnabled = sessionReplayTarget is SessionReplayTarget
            val isWebViewMonitoringEnabled = webViewConfiguration != null
            val baseFields =
                fieldsOf(
                    "_app_installation_source" to installationSource,
                    "_capture_start_thread" to captureStartThread,
                    "_native_load_duration_ms" to
                        sdkConfiguredDuration.nativeLoadDuration.toDouble(DurationUnit.MILLISECONDS).toString(),
                    "_logger_build_duration_ms" to
                        sdkConfiguredDuration.loggerImplBuildDuration.toDouble(DurationUnit.MILLISECONDS).toString(),
                    "_session_replay_enabled" to isSessionReplayEnabled.toString(),
                    "_webview_monitoring_enabled" to isWebViewMonitoringEnabled.toString(),
                )
            val fatalIssueFields =
                (
                    issueReporter?.getLogStatusFieldsMap()
                        ?: IssueReporter.getDisabledStatusFieldsMap()
                ).toFields()

            val sdkStartFields = combineFields(baseFields, fatalIssueFields, webViewConfiguration.toFields())
            CaptureJniLibrary.writeSDKStartLog(
                this.loggerId,
                sdkStartFields.toLegacyJniFields(),
                sdkConfiguredDuration.wholeStartDuration.toDouble(DurationUnit.SECONDS),
            )
        }
    }

    internal fun getIssueProcessor(): IIssueReporterProcessor? = issueReporter?.getIssueReporterProcessor()

    private fun startDebugOperationsAsNeeded(context: Context) {
        if (!BuildTypeChecker.isDebuggable(context)) {
            return
        }

        createTemporaryDeviceCode { result ->
            if (result is CaptureResult.Success) {
                Log.i("capture", "Temporary device code: ${result.value}")
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
                )
            jankStatsMonitor?.let {
                eventsListenerTarget.add(it)
            }
        } else {
            errorHandler.handleError("Couldn't start JankStatsMonitor. Invalid application provided")
        }
    }
}

internal sealed class LogAttributesOverrides {
    data class PreviousRunSessionId(
        val occurredAtTimestampMs: Long,
    ) : LogAttributesOverrides()

    data class OccurredAt(
        val occurredAtTimestampMs: Long,
    ) : LogAttributesOverrides()
}
