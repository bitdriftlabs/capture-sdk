// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ApplicationExitInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueReporterState.Initialized.ProcessingFailure
import io.bitdrift.capture.reports.appexit.AnrStackTraceProcessor
import io.bitdrift.capture.reports.appexit.NativeCrashStackTraceProcessor
import io.bitdrift.capture.reports.appexit.ProcessedResult
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of fatal issues (See [FatalIssueType])
 */
internal class FatalIssueReporter(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IFatalIssueReporter {
    private val appContext by lazy { APP_CONTEXT }

    @VisibleForTesting
    internal var status: FatalIssueReporterStatus =
        FatalIssueReporterStatus(FatalIssueReporterState.NotInitialized)
        private set

    /**
     * To be called prior Capture.Logger.start()
     */
    override fun init() {
        if (status.state is FatalIssueReporterState.NotInitialized) {
            processPriorReportFiles()
        } else {
            Log.w("capture", "Fatal issue reporting already being initialized")
        }
    }

    /**
     * Pre-process existing [ApplicationExitInfo] trace and stores to ensure proper upload
     * on all network conditions
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun processAndStoreAppExitInfoTrace(
        errorHandler: ErrorHandler,
        applicationExitReason: ApplicationExitInfo,
    ) {
        val traceInputStream = applicationExitReason.traceInputStream ?: return
        val processedResult =
            when (applicationExitReason.reason) {
                ApplicationExitInfo.REASON_ANR -> AnrStackTraceProcessor.process(traceInputStream)
                ApplicationExitInfo.REASON_CRASH_NATIVE -> NativeCrashStackTraceProcessor.process(traceInputStream)
                else -> {
                    ProcessedResult.Failed("Unexpected Fatal Issue type passed")
                }
            }
        if (processedResult is ProcessedResult.Failed) {
            errorHandler.handleError(processedResult.errorDetails)
        } else if (processedResult is ProcessedResult.Success) {
            storeReport(
                errorHandler,
                applicationExitReason.timestamp,
                processedResult.fatalIssueType,
                processedResult.traceContents,
            )
        }
    }

    override fun getFatalIssueFieldMap(): Map<String, FieldValue> =
        buildMap {
            put(
                FATAL_ISSUE_REPORTING_STATE_KEY,
                status.state.readableType.toFieldValue(),
            )
            put(
                FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY,
                getInitDurationMs().toFieldValue(),
            )
        }

    @VisibleForTesting
    internal fun getInitDurationMs(): String = status.duration?.toDouble(DurationUnit.MILLISECONDS)?.toString() ?: "n/a"

    /**
     * Stores a report with detailed stack trace under [DESTINATION_FILE_PATH]
     */
    private fun storeReport(
        errorHandler: ErrorHandler,
        appTerminationTime: Long,
        fatalIssueType: FatalIssueType,
        fileContents: String,
    ) {
        val fileName =
            appTerminationTime.toString() + FILE_NAME_SEPARATOR + fatalIssueType.readableType.lowercase()
        val destinationDirectory =
            File(
                SdkDirectory.getPath(appContext),
                DESTINATION_FILE_PATH,
            ).apply { if (!exists()) mkdirs() }
        val fileToWrite = File(destinationDirectory, fileName)
        try {
            PrintWriter(fileToWrite).use { out -> out.println(fileContents) }
        } catch (ioException: Exception) {
            errorHandler.handleError("Couldn't persist $fileName for ${fatalIssueType.readableType}. ${ioException.message}")
        }
    }

    /**
     * Process existing stored files with fatal issue information
     */
    private fun processPriorReportFiles() =
        runCatching {
            mainThreadHandler.runAndReturnResult {
                var fatalIssueReporterState: FatalIssueReporterState
                val duration =
                    measureTime {
                        fatalIssueReporterState = verifyDirectoriesAndCopyFiles()
                    }
                status =
                    FatalIssueReporterStatus(fatalIssueReporterState, duration)
            }
        }.getOrElse {
            status =
                FatalIssueReporterStatus(ProcessingFailure("Error while processCrashReportFile. ${it.message}"))
        }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): FatalIssueReporterState {
        val sdkDirectory = SdkDirectory.getPath(appContext)
        val crashConfigFile = File(sdkDirectory, CONFIGURATION_FILE_PATH)

        if (!crashConfigFile.exists()) {
            return FatalIssueReporterState.Initialized.MissingConfigFile
        }

        val crashConfigFileContents = crashConfigFile.readText()
        val crashConfigDetails =
            getConfigDetails(crashConfigFileContents) ?: let {
                return FatalIssueReporterState.Initialized.MalformedConfigFile
            }

        val sourceDirectory =
            File(appContext.cacheDir.absolutePath, crashConfigDetails.rootPath)
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory) {
            return FatalIssueReporterState.Initialized.WithoutPriorFatalIssue
        }

        val destinationDirectory =
            File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }

        return runCatching {
            findAndCopyPriorReportFile(
                sourceDirectory,
                destinationDirectory,
                crashConfigDetails.extensionFileName,
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

    private fun getConfigDetails(crashConfigFileContent: String): ConfigDetails? =
        runCatching {
            val crashConfigDetails = crashConfigFileContent.split(",")
            ConfigDetails(crashConfigDetails[0], crashConfigDetails[1])
        }.getOrNull()

    private fun findCrashFile(
        sourceFile: File,
        fileExtension: String,
    ): File? =
        sourceFile.walk().firstOrNull {
            it.isFile && it.extension == fileExtension
        }

    private fun File.toFilenameWithTimeStamp(): String {
        val fileCreationEpochTime = getFileCreationTimeEpochInMillis(this)
        return fileCreationEpochTime.toString() + FILE_NAME_SEPARATOR + this.name
    }

    private fun getFileCreationTimeEpochInMillis(file: File): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fileTime =
                Files.getAttribute(file.toPath(), LAST_MODIFIED_TIME_ATTRIBUTE) as FileTime
            fileTime.toMillis()
        } else {
            file.lastModified()
        }

    private data class ConfigDetails(
        val rootPath: String,
        val extensionFileName: String,
    )

    private companion object {
        private const val CONFIGURATION_FILE_PATH = "/reports/config"
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY =
            "_fatal_issue_reporting_duration_ms"
        private const val FATAL_ISSUE_REPORTING_STATE_KEY = "_fatal_issue_reporting_state"
        private const val DESTINATION_FILE_PATH = "/reports/new"
        private const val LAST_MODIFIED_TIME_ATTRIBUTE = "lastModifiedTime"
        private const val FILE_NAME_SEPARATOR = "_"
    }
}
