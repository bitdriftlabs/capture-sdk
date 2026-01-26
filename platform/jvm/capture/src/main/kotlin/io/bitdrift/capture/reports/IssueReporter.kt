// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ActivityManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.reports.IssueReporterState.NotInitialized
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.mapToFatalIssueType
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener
import io.bitdrift.capture.reports.persistence.IssueReporterStorage
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.IssueReporterProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.ConfigCache
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Reports different Issue Types (JVM crash, ANR, native, StrictMode, etc).
 *
 * @param backgroundThreadHandler Handler for background thread operations.
 * @param latestAppExitInfoProvider Provider for retrieving latest app exit information.
 * @param captureUncaughtExceptionHandler Handler for uncaught exceptions.
 */
internal class IssueReporter(
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
    private val dateProvider: DateProvider,
) : IIssueReporter,
    IJvmCrashListener {
    @VisibleForTesting
    internal var issueReporterState: IssueReporterState = NotInitialized
        private set
    private var initializationDuration: Duration? = null

    private var issueReporterProcessor: IssueReporterProcessor? = null

    /**
     * Initializes the IssueReporter handler once we have the required dependencies available
     */
    override fun init(
        activityManager: ActivityManager,
        sdkDirectory: String,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    ) {
        if (issueReporterState != NotInitialized) {
            Log.e(LOG_TAG, "Issue reporting already being initialized")
            return
        }
        // setting immediate value to avoid re-initializing if the first state check takes time
        issueReporterState = IssueReporterState.Initializing

        val duration = TimeSource.Monotonic.markNow()
        runCatching {
            runCatching {
                if (!isFatalIssueReportingRuntimeEnabled(sdkDirectory)) {
                    issueReporterState = IssueReporterState.RuntimeDisabled
                    initializationDuration = duration.elapsedNow()
                    return
                }
            }.onFailure {
                issueReporterState =
                    if (it is FileNotFoundException) {
                        IssueReporterState.RuntimeUnset
                    } else {
                        IssueReporterState.RuntimeInvalid
                    }
                initializationDuration = duration.elapsedNow()
                return
            }

            issueReporterProcessor =
                IssueReporterProcessor(
                    IssueReporterStorage(sdkDirectory),
                    clientAttributes,
                    CaptureJniLibrary,
                    dateProvider,
                )
            captureUncaughtExceptionHandler.install(this)

            backgroundThreadHandler.runAsync {
                runCatching {
                    persistLastExitReasonIfNeeded(activityManager)
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
        }.getOrElse {
            logError(completedReportsProcessor, it)
            issueReporterState =
                IssueReporterState.InitializationFailed
        }
        initializationDuration = duration.elapsedNow()
    }

    /**
     * Returns the underlying report processor
     */
    internal fun getIssueReporterProcessor(): IssueReporterProcessor? = issueReporterProcessor

    /**
     * Returns the current init state
     */
    override fun initializationState(): IssueReporterState = issueReporterState

    /**
     * Persists any JVM crash
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        runCatching {
            issueReporterProcessor?.persistJvmCrash(
                callerThread = thread,
                throwable = throwable,
                allThreads = Thread.getAllStackTraces(),
            )
        }.getOrElse {
            val errorMessage = "Error while persisting JVM crash. $it"
            Log.e(LOG_TAG, errorMessage)
        }
    }

    override fun getLogStatusFieldsMap(): Map<String, String> =
        buildMap {
            put(FATAL_ISSUE_REPORTING_STATE_KEY, issueReporterState.readableType)
            initializationDuration?.let {
                put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, it.toDouble(DurationUnit.MILLISECONDS).toString())
            }
        }

    private fun persistLastExitReasonIfNeeded(activityManager: ActivityManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val lastReasonResult = latestAppExitInfoProvider.get(activityManager)
        if (lastReasonResult is LatestAppExitReasonResult.Valid) {
            val lastReason = lastReasonResult.applicationExitInfo
            lastReason.traceInputStream?.use {
                mapToFatalIssueType(lastReason.reason)?.let { fatalIssueType ->
                    issueReporterProcessor?.persistAppExitReport(
                        fatalIssueType = fatalIssueType,
                        timestamp = lastReason.timestamp,
                        description = lastReason.description,
                        traceInputStream = it,
                    )
                }
            }
        }
    }

    private fun isFatalIssueReportingRuntimeEnabled(sdkDirectory: String): Boolean {
        val configFile = File(sdkDirectory, "reports/config.csv")
        val config = ConfigCache.readValues(configFile)
        return config.get("crash_reporting.enabled") == true
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
    }
}
