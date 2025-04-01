// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueConfigParser.getFatalIssueConfigDetails
import io.bitdrift.capture.reports.FatalIssueReporterState.Initialized.ProcessingFailure
import io.bitdrift.capture.reports.persistence.FatalIssueReporterStorage
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of crashes
 */
internal class FatalIssueReporter(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IFatalIssueReporter {
    private val appContext = APP_CONTEXT

    private val sdkDirectory by lazy { SdkDirectory.getPath(appContext) }
    private val destinationDirectory: File by lazy {
        File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }
    }
    private val fatalIssueReporterStorage: FatalIssueReporterStorage by lazy {
        FatalIssueReporterStorage(destinationDirectory)
    }

    private var fatalIssueReporterStatus: FatalIssueReporterStatus =
        FatalIssueReporterStatus(
            FatalIssueReporterState.NotInitialized,
            mechanism = FatalIssueMechanism.NONE,
        )

    override fun initialize(fatalIssueMechanism: FatalIssueMechanism) {
        if (fatalIssueReporterStatus.state is FatalIssueReporterState.NotInitialized) {
            fatalIssueReporterStatus =
                if (fatalIssueMechanism == FatalIssueMechanism.CUSTOM_CONFIG) {
                    processPriorCustomReportFiles()
                } else {
                    FatalIssueReporterStatus(
                        FatalIssueReporterState.BuildInInitialized,
                        mechanism = fatalIssueMechanism,
                    )
                }
        } else {
            Log.w("capture", "Fatal issue reporting already being initialized")
        }
    }

    override fun fetchStatus(): FatalIssueReporterStatus = fatalIssueReporterStatus

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> =
        buildMap {
            put(
                FATAL_ISSUE_REPORTING_STATE_KEY,
                fatalIssueReporterStatus.state.readableType.toFieldValue(),
            )
            put(
                FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY,
                fatalIssueReporterStatus.getDuration().toFieldValue(),
            )
        }

    override fun persistJvmCrash(
        errorHandler: ErrorHandler,
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
    ) {
        fatalIssueReporterStorage
            .persistJvmCrash(errorHandler, timestamp, callerThread, throwable)
    }

    override fun persistAppExitReport(
        errorHandler: ErrorHandler,
        timestamp: Long,
        exitReasonType: Int,
        traceInputStream: InputStream,
    ) {
        fatalIssueReporterStorage
            .persistAppExitReport(errorHandler, timestamp, exitReasonType, traceInputStream)
    }

    /**
     * Process existing crash report files.
     *
     * NOTE: This is only applicable for [FatalIssueMechanism.CUSTOM_CONFIG]
     *
     * @return The status of the crash reporting processing
     */
    private fun processPriorCustomReportFiles(): FatalIssueReporterStatus {
        val mechanism = FatalIssueMechanism.CUSTOM_CONFIG
        return runCatching {
            mainThreadHandler.runAndReturnResult {
                var fatalIssueReporterState: FatalIssueReporterState
                val duration =
                    measureTime {
                        fatalIssueReporterState = verifyDirectoriesAndCopyFiles()
                    }
                FatalIssueReporterStatus(
                    fatalIssueReporterState,
                    duration,
                    mechanism,
                )
            }
        }.getOrElse {
            FatalIssueReporterStatus(
                ProcessingFailure("Error while processCrashReportFile. ${it.message}"),
                mechanism = mechanism,
            )
        }
    }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): FatalIssueReporterState {
        val fatalIssueConfigFile = File(sdkDirectory, CONFIGURATION_FILE_PATH)

        if (!fatalIssueConfigFile.exists()) {
            return FatalIssueReporterState.Initialized.MissingConfigFile
        }

        val fatalIssueConfigFileContents = fatalIssueConfigFile.readText()
        val fatalIssueConfigDetails =
            getFatalIssueConfigDetails(appContext, fatalIssueConfigFileContents) ?: let {
                return FatalIssueReporterState.Initialized.MalformedConfigFile
            }
        if (fatalIssueConfigDetails.sourceDirectory.isInvalidDirectory()) {
            return FatalIssueReporterState.Initialized.InvalidCrashConfigDirectory
        }

        return runCatching {
            findAndCopyPriorReportFile(
                fatalIssueConfigDetails.sourceDirectory,
                destinationDirectory,
                fatalIssueConfigDetails.extensionFileName,
            )
        }.getOrElse {
            return ProcessingFailure("Couldn't process crash files. ${it.message}")
        }
    }

    @UiThread
    private fun findAndCopyPriorReportFile(
        sourceDirectory: File,
        destinationDirectory: File,
        fileExtension: String,
    ): FatalIssueReporterState {
        val crashFile =
            findCrashFile(sourceDirectory, fileExtension)
                ?: let {
                    return FatalIssueReporterState.Initialized.WithoutPriorFatalIssue
                }

        verifyDirectoryIsEmpty(destinationDirectory)
        val destinationFile = File(destinationDirectory, crashFile.toFilenameWithTimeStamp())
        crashFile.copyTo(destinationFile, overwrite = true)

        return if (destinationFile.exists()) {
            FatalIssueReporterState.Initialized.FatalIssueReportSent
        } else {
            FatalIssueReporterState.Initialized.WithoutPriorFatalIssue
        }
    }

    private fun verifyDirectoryIsEmpty(directory: File) {
        val files = directory.listFiles()
        if (files.isNullOrEmpty()) {
            return
        }
        files.forEach {
            it.delete()
        }
    }

    private fun findCrashFile(
        sourceFile: File,
        fileExtension: String,
    ): File? =
        sourceFile
            .walk()
            .filter { it.isFile && it.extension == fileExtension }
            .maxByOrNull { it.lastModified() }

    private fun File.toFilenameWithTimeStamp(): String {
        val fileCreationEpochTime = getFileCreationTimeEpochInMillis(this)
        return fileCreationEpochTime.toString() + "_" + this.name
    }

    private fun getFileCreationTimeEpochInMillis(file: File): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fileTime =
                Files.getAttribute(file.toPath(), LAST_MODIFIED_TIME_ATTRIBUTE) as FileTime
            fileTime.toMillis()
        } else {
            file.lastModified()
        }

    private fun File.isInvalidDirectory(): Boolean = !exists() || !isDirectory

    internal companion object {
        private const val CONFIGURATION_FILE_PATH = "/reports/config"
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY =
            "_fatal_issue_reporting_duration_ms"
        private const val FATAL_ISSUE_REPORTING_STATE_KEY = "_fatal_issue_reporting_state"
        private const val DESTINATION_FILE_PATH = "/reports/new"
        private const val LAST_MODIFIED_TIME_ATTRIBUTE = "lastModifiedTime"

        /**
         * Returns the fields map with latest [FatalIssueReporterStatus]
         */
        internal fun FatalIssueReporterStatus.buildFieldsMap(): Map<String, FieldValue> =
            buildMap {
                put(FATAL_ISSUE_REPORTING_STATE_KEY, state.readableType.toFieldValue())
                put(FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY, getDuration().toFieldValue())
            }

        @VisibleForTesting
        fun FatalIssueReporterStatus.getDuration(): String = duration?.toDouble(DurationUnit.MILLISECONDS)?.toString() ?: "n/a"
    }
}
