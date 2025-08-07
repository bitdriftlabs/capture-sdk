// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.Capture.LOG_TAG
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
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of crashes
 */
internal class FatalIssueReporter(
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
) : IFatalIssueReporter,
    IJvmCrashListener {
    @VisibleForTesting
    internal var fatalIssueReporterStatus: FatalIssueReporterStatus = buildDefaultReporterStatus()
        private set

    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor

    /**
     * Initializes a BuiltIn Fatal Issue reporting mechanism that doesn't depend on any 3rd party
     * libraries
     */
    override fun initBuiltInMode(
        appContext: Context,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    ) {
        if (fatalIssueReporterStatus.state is NotInitialized) {
            runCatching {
                var fatalIssueReporterState: FatalIssueReporterState
                val duration =
                    measureTime {
                        val destinationDirectory = getFatalIssueDirectories(appContext)
                        fatalIssueReporterProcessor =
                            FatalIssueReporterProcessor(
                                FatalIssueReporterStorage(destinationDirectory.destinationDirectory),
                                clientAttributes,
                            )
                        captureUncaughtExceptionHandler.install(this)
                        backgroundThreadHandler.runAsync {
                            persistLastExitReasonIfNeeded(appContext)
                            completedReportsProcessor.processCrashReports()
                        }
                        fatalIssueReporterState = FatalIssueReporterState.BuiltIn.Initialized
                    }
                fatalIssueReporterState to duration
                fatalIssueReporterStatus =
                    FatalIssueReporterStatus(
                        fatalIssueReporterState,
                        duration,
                        FatalIssueMechanism.BuiltIn,
                    )
            }.getOrElse {
                val errorMessage =
                    "Error while initializing reporter for ${FatalIssueMechanism.BuiltIn}. $it"
                Log.e(LOG_TAG, errorMessage)
                fatalIssueReporterStatus =
                    FatalIssueReporterStatus(
                        FatalIssueReporterState.BuiltIn.InitializationFailed,
                        mechanism = FatalIssueMechanism.BuiltIn,
                    )
            }
        } else {
            Log.e(LOG_TAG, "Fatal issue reporting already being initialized")
        }
    }

    /**
     * Returns the configured [io.bitdrift.capture.reports.FatalIssueMechanism]
     */
    override fun getReportingMechanism(): FatalIssueMechanism = fatalIssueReporterStatus.mechanism

    /**
     * Applicable when [FatalIssueMechanism.BuiltIn] is available, given that registration
     * only occurs for calls like initialize(FatalIssueMechanism.BUILT_IN)
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        runCatching {
            fatalIssueReporterProcessor.persistJvmCrash(
                timestamp = System.currentTimeMillis(),
                callerThread = thread,
                throwable = throwable,
                allThreads = Thread.getAllStackTraces(),
            )
        }.getOrElse {
            val errorMessage = "Error while initializing reporter for ${FatalIssueMechanism.BuiltIn}. $it"
            Log.e(LOG_TAG, errorMessage)
        }
    }

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> =
        buildMap {
            put(FATAL_ISSUE_REPORTING_STATE_KEY, fatalIssueReporterStatus.state.readableType.toFieldValue())
            fatalIssueReporterStatus.getDuration()?.let { duration ->
                put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, duration)
            }
        }

    private fun persistLastExitReasonIfNeeded(appContext: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val activityManager: ActivityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lastReasonResult = latestAppExitInfoProvider.get(activityManager)
        if (lastReasonResult is LatestAppExitReasonResult.Valid) {
            val lastReason = lastReasonResult.applicationExitInfo
            lastReason.traceInputStream?.let {
                mapToFatalIssueType(lastReason.reason)?.let { fatalIssueType ->
                    fatalIssueReporterProcessor.persistAppExitReport(
                        fatalIssueType = fatalIssueType,
                        timestamp = lastReason.timestamp,
                        description = lastReason.description,
                        traceInputStream = it,
                    )
                }
            }
        }
    }

    private fun buildDefaultReporterStatus(): FatalIssueReporterStatus =
        FatalIssueReporterStatus(
            FatalIssueReporterState.NotInitialized,
            mechanism = FatalIssueMechanism.BuiltIn,
        )

    private fun getFatalIssueDirectories(appContext: Context): FatalIssueDirectories {
        val sdkDirectory: String = SdkDirectory.getPath(appContext)
        val destinationDirectory = File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }
        return FatalIssueDirectories(sdkDirectory, destinationDirectory)
    }

    private data class FatalIssueDirectories(
        val sdkDirectoryPath: String,
        val destinationDirectory: File,
    )

    internal companion object {
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY = "_fatal_issue_reporting_duration_ms"
        private const val FATAL_ISSUE_REPORTING_STATE_KEY = "_fatal_issue_reporting_state"
        private const val DESTINATION_FILE_PATH = "/reports/new"

        /**
         * Returns the fields map with latest [FatalIssueReporterStatus]
         */
        internal fun FatalIssueReporterStatus.buildFieldsMap(): Map<String, FieldValue> =
            buildMap {
                put(FATAL_ISSUE_REPORTING_STATE_KEY, state.readableType.toFieldValue())
                getDuration()?.let {
                    put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, it)
                }
            }

        @VisibleForTesting
        fun FatalIssueReporterStatus.getDuration(): FieldValue? = duration?.toFieldValue(DurationUnit.MILLISECONDS)
    }
}
