// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LoggerId
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.events.performance.IMemoryMetricsProvider
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.reports.IssueReporterState.RuntimeState
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener
import io.bitdrift.capture.reports.persistence.IssueReporterStore
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.IIssueReporterProcessor
import io.bitdrift.capture.reports.processor.IssueReporterProcessor
import io.bitdrift.capture.reports.processor.JniStreamingReportProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.ConfigCache
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Reports different Issue Types (JVM crash, ANR, native, StrictMode, etc).
 *
 * Whether this reporter owns uncaught JVM exceptions is decided exactly once, inside the
 * constructor: the persisted runtime config is read and the report processor is built there, so
 * [handlesJvmCrashes] is immutable and safely published to every thread by constructor semantics.
 * The only deferred work is [processPriorReports], which handles reports persisted by previous
 * runs once the logger is able to receive them; its outcome updates the telemetry state but never
 * changes who owns JVM crashes.
 *
 * @param internalLogger Logger used for internal SDK errors/diagnostics.
 * @param backgroundThreadHandler Handler for background thread operations.
 * @param latestAppExitInfoProvider Provider for retrieving latest app exit information.
 * @param dateProvider Date source used when building report payload metadata.
 * @param memoryMetricsProvider Provider for memory metrics attached to reports.
 * @param sdkDirectory Directory holding the persisted reports and their runtime config.
 * @param clientAttributes Static client attributes attached to reports.
 * @param loggerId Handle of the native logger that receives the processed reports.
 */
internal class IssueReporter(
    private val internalLogger: IInternalLogger,
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider,
    private val dateProvider: DateProvider,
    private val memoryMetricsProvider: IMemoryMetricsProvider,
    sdkDirectory: String,
    clientAttributes: IClientAttributes,
    loggerId: LoggerId,
) : IJvmCrashListener {
    private val issueReporterProcessor: IIssueReporterProcessor?
    private val constructionError: Throwable?
    private val initializationDuration: Duration

    // Telemetry only: reported on the SDK start log and asserted by tests, never consulted to
    // route a crash. The Initialized/InitializationFailed transition happens on the background
    // worker once prior reports are processed.
    @VisibleForTesting
    internal var issueReporterState: IssueReporterState
        private set

    init {
        val duration = TimeSource.Monotonic.markNow()
        val runtimeState = getRuntimeState(sdkDirectory)
        if (runtimeState == RuntimeState.Enabled) {
            val processorResult =
                runCatching {
                    buildDefaultIssueReporterProcessor(
                        sdkDirectory,
                        clientAttributes,
                        loggerId,
                        dateProvider,
                        internalLogger,
                        memoryMetricsProvider,
                    )
                }
            issueReporterProcessor = processorResult.getOrNull()
            constructionError =
                processorResult.exceptionOrNull()?.also {
                    Log.e(LOG_TAG, "Error while initializing reporter. $it", it)
                }
            issueReporterState =
                if (issueReporterProcessor != null) {
                    IssueReporterState.Initializing
                } else {
                    IssueReporterState.InitializationFailed
                }
        } else {
            issueReporterProcessor = null
            constructionError = null
            issueReporterState = runtimeState
        }
        initializationDuration = duration.elapsedNow()
    }

    /**
     * Whether this reporter owns uncaught JVM exceptions. Fixed at construction, so the wiring in
     * `LoggerImpl` can register exactly one JVM crash reporter and no listener ever has to
     * re-negotiate ownership at crash time.
     */
    val handlesJvmCrashes: Boolean
        get() = issueReporterProcessor != null

    /**
     * Returns the underlying report processor
     */
    internal fun getIssueReporterProcessor(): IIssueReporterProcessor? = issueReporterProcessor

    /**
     * Processes issue reports persisted by previous app runs. Must be called once, after the
     * Capture logger is started, because report processing can trigger `onBeforeReportSend`
     * callbacks that resolve `Capture.logger()`.
     */
    fun processPriorReports(completedReportsProcessor: ICompletedReportsProcessor) {
        constructionError?.let {
            completedReportsProcessor.onReportProcessingError("Error while initializing reporter. $it", it)
        }
        if (issueReporterState != IssueReporterState.Initializing) {
            return
        }
        backgroundThreadHandler.runAsync {
            runCatching {
                persistLastExitReasonIfNeeded()
                completedReportsProcessor.processIssueReports(ReportProcessingSession.PreviousRun)
            }.onSuccess {
                issueReporterState =
                    IssueReporterState.Initialized
            }.onFailure {
                logError(completedReportsProcessor, it)
                issueReporterState =
                    IssueReporterState.InitializationFailed
            }
        }
    }

    /**
     * Processes any JVM crash.
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        internalLogger.notifyMemoryPressureLevel(memoryMetricsProvider.getCurrentJvmMemoryPressureLevel())

        issueReporterProcessor?.processJvmCrash(
            callerThread = thread,
            throwable = throwable,
            allThreads = Thread.getAllStackTraces(),
        )
    }

    fun getLogStatusFieldsMap(): Map<String, String> =
        buildMap {
            put(FATAL_ISSUE_REPORTING_STATE_KEY, issueReporterState.readableType)
            put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, initializationDuration.toDouble(DurationUnit.MILLISECONDS).toString())
        }

    private fun getRuntimeState(sdkDirectory: String): RuntimeState =
        runCatching {
            val configFile = File(sdkDirectory, "reports/config.csv")

            // For initial app installation/clear cache.
            // The configuration wasn't written to disk yet, so we are intentionally enabling crash
            // reporting to not miss any of those early crashes
            if (!configFile.exists()) {
                return RuntimeState.Enabled
            }

            val config = ConfigCache.readValues(configFile)
            return when (config["crash_reporting.enabled"]) {
                true -> RuntimeState.Enabled
                false -> RuntimeState.Disabled
                else -> RuntimeState.MissingFlag
            }
        }.getOrElse {
            RuntimeState.Invalid
        }

    private fun persistLastExitReasonIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val latestAppExitReasonResult = latestAppExitInfoProvider.get()
        if (latestAppExitReasonResult is LatestAppExitReasonResult.Valid) {
            issueReporterProcessor?.processAppExitReport(latestAppExitReasonResult.applicationExitInfo)
        }
    }

    private fun logError(
        completedReportsProcessor: ICompletedReportsProcessor,
        throwable: Throwable,
    ) {
        val errorMessage =
            "Error while initializing reporter. $throwable"
        completedReportsProcessor.onReportProcessingError(errorMessage, throwable)
        Log.e(LOG_TAG, errorMessage, throwable)
    }

    internal companion object {
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY = "_fatal_issue_reporting_duration_ms"
        private const val FATAL_ISSUE_REPORTING_STATE_KEY = "_fatal_issue_reporting_state"

        fun getDisabledStatusFieldsMap(): Map<String, String> =
            buildMap {
                put(FATAL_ISSUE_REPORTING_STATE_KEY, IssueReporterState.ClientDisabled.readableType)
            }

        fun buildDefaultIssueReporterProcessor(
            sdkDirectory: String,
            clientAttributes: IClientAttributes,
            loggerId: LoggerId,
            dateProvider: DateProvider,
            internalLogger: IInternalLogger,
            memoryMetricsProvider: IMemoryMetricsProvider,
        ): IIssueReporterProcessor =
            IssueReporterProcessor(
                IssueReporterStore(sdkDirectory),
                clientAttributes,
                JniStreamingReportProcessor(loggerId, clientAttributes),
                dateProvider,
                internalLogger,
                memoryMetricsProvider,
            )
    }
}
