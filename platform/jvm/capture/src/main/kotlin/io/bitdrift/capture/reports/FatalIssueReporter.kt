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
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueReporterState.NotInitialized
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.mapToFatalIssueType
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener
import io.bitdrift.capture.reports.persistence.FatalIssueReporterStorage
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.ConfigCache
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Handles internal reporting of crashes
 * @param enableNativeCrashReporting Flag to enable native NDK crash reporting.
 * Note: This is a temporary flag that may be removed in the future.
 * @param backgroundThreadHandler Handler for background thread operations
 * @param latestAppExitInfoProvider Provider for retrieving latest app exit information
 * @param captureUncaughtExceptionHandler Handler for uncaught exceptions
 */
internal class FatalIssueReporter(
    private val enableNativeCrashReporting: Boolean = false,
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
) : IFatalIssueReporter,
    IJvmCrashListener {
    @VisibleForTesting
    internal var fatalIssueReporterState: FatalIssueReporterState = NotInitialized
        private set
    private var initializationDuration: Duration? = null

    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor

    /**
     * Initializes the FatalIssueReporter handler once we have the required dependencies available
     */
    override fun init(
        activityManager: ActivityManager,
        sdkDirectory: String,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    ) {
        if (fatalIssueReporterState != NotInitialized) {
            Log.e(LOG_TAG, "Fatal issue reporting already being initialized")
            return
        }
        // setting immediate value to avoid re-initializing if the first state check takes time
        fatalIssueReporterState = FatalIssueReporterState.Initializing

        val duration = TimeSource.Monotonic.markNow()
        runCatching {
            runCatching {
                if (!isFatalIssueReportingRuntimeEnabled(sdkDirectory)) {
                    fatalIssueReporterState = FatalIssueReporterState.RuntimeDisabled
                    initializationDuration = duration.elapsedNow()
                    return
                }
            }.onFailure {
                fatalIssueReporterState =
                    if (it is FileNotFoundException) {
                        FatalIssueReporterState.RuntimeUnset
                    } else {
                        FatalIssueReporterState.RuntimeInvalid
                    }
                initializationDuration = duration.elapsedNow()
                return
            }

            val destinationDirectory = getFatalIssueDirectories(sdkDirectory)
            fatalIssueReporterProcessor =
                FatalIssueReporterProcessor(
                    FatalIssueReporterStorage(destinationDirectory.destinationDirectory),
                    clientAttributes,
                    CaptureJniLibrary,
                )
            captureUncaughtExceptionHandler.install(this)

            backgroundThreadHandler.runAsync {
                runCatching {
                    persistLastExitReasonIfNeeded(activityManager)
                    // TODO: Remove this temporary call - should be called when JS error reporting is enabled
                    enableJavaScriptReporting(sdkDirectory)
                    completedReportsProcessor.processIssueReports(ReportProcessingSession.PreviousRun)
                }.onSuccess {
                    fatalIssueReporterState =
                        FatalIssueReporterState.Initialized
                }.onFailure {
                    logError(completedReportsProcessor, it)
                    fatalIssueReporterState =
                        FatalIssueReporterState.InitializationFailed
                }
            }
        }.getOrElse {
            logError(completedReportsProcessor, it)
            fatalIssueReporterState =
                FatalIssueReporterState.InitializationFailed
        }
        initializationDuration = duration.elapsedNow()
    }

    /**
     * Returns the current init state
     */
    override fun initializationState(): FatalIssueReporterState = fatalIssueReporterState

    /**
     * Persists any JVM crash
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
        isNonFatal: Boolean,
    ) {
        runCatching {
            if (isNonFatal) {
                fatalIssueReporterProcessor.persistNonFatalJvmCrash(
                    timestamp = System.currentTimeMillis(),
                    callerThread = thread,
                    throwable = throwable,
                    allThreads = Thread.getAllStackTraces(),
                )
                return
            }
            fatalIssueReporterProcessor.persistJvmCrash(
                timestamp = System.currentTimeMillis(),
                callerThread = thread,
                throwable = throwable,
                allThreads = Thread.getAllStackTraces(),
            )
        }.getOrElse {
            val errorMessage = "Error while persisting JVM crash. $it"
            Log.e(LOG_TAG, errorMessage)
        }
    }

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> =
        buildMap {
            put(FATAL_ISSUE_REPORTING_STATE_KEY, fatalIssueReporterState.readableType.toFieldValue())
            initializationDuration?.toFieldValue(DurationUnit.MILLISECONDS)?.let {
                put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, it)
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
                    fatalIssueReporterProcessor.persistAppExitReport(
                        fatalIssueType = fatalIssueType,
                        enableNativeCrashReporting = enableNativeCrashReporting,
                        timestamp = lastReason.timestamp,
                        description = lastReason.description,
                        traceInputStream = it,
                    )
                }
            }
        }
    }

    private fun getFatalIssueDirectories(sdkDirectory: String): FatalIssueDirectories {
        val destinationDirectory = File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }
        return FatalIssueDirectories(sdkDirectory, destinationDirectory)
    }

    private fun isFatalIssueReportingRuntimeEnabled(sdkDirectory: String): Boolean {
        val configFile = File(sdkDirectory, "reports/config.csv")
        val config = ConfigCache.readValues(configFile)
        return config.get("crash_reporting.enabled") == true
    }

    /**
     * Temporarily enables JavaScript error reporting by writing to js_config.csv
     * TODO: This should be called when JS error reporting is actually enabled, not always
     */
    private fun enableJavaScriptReporting(sdkDirectory: String) {
        runCatching {
            val jsConfigFile = File(sdkDirectory, "reports/js_config.csv")
            val reportsDir = jsConfigFile.parentFile
            if (reportsDir != null && !reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            // Check if the flag already exists
            if (jsConfigFile.exists()) {
                val existingContent = jsConfigFile.readText()
                if (existingContent.contains("java_script_reporting.enabled")) {
                    return
                }
            }

            // Write the flag to js_config.csv
            jsConfigFile.writeText("java_script_reporting.enabled,true")

            Log.d(LOG_TAG, "Enabled JavaScript error reporting in js_config.csv")
        }.onFailure {
            Log.w(LOG_TAG, "Failed to enable JavaScript error reporting: $it")
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

    private data class FatalIssueDirectories(
        val sdkDirectoryPath: String,
        val destinationDirectory: File,
    )

    internal companion object {
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY = "_fatal_issue_reporting_duration_ms"
        private const val FATAL_ISSUE_REPORTING_STATE_KEY = "_fatal_issue_reporting_state"
        private const val DESTINATION_FILE_PATH = "/reports/new"

        fun getDisabledStatusFieldsMap(): Map<String, FieldValue> =
            buildMap {
                put(FATAL_ISSUE_REPORTING_STATE_KEY, FatalIssueReporterState.ClientDisabled.readableType.toFieldValue())
            }
    }
}
