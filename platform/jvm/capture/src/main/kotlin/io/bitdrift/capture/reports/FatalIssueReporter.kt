package io.bitdrift.capture.reports

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueReporterState.Initialized.ProcessingFailure
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.mapToFatalIssueType
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.JvmCrashListener
import io.bitdrift.capture.reports.parser.FatalIssueConfigParser.getFatalIssueConfigDetails
import io.bitdrift.capture.reports.persistence.FatalIssueReporterStorage
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of crashes
 */
internal class FatalIssueReporter(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
) : IFatalIssueReporter,
    JvmCrashListener {
    private val appContext = APP_CONTEXT
    private val sdkDirectory = SdkDirectory.getPath(appContext)
    private val destinationDirectory: File by lazy {
        File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }
    }
    private val fatalIssueReporterProcessor: FatalIssueReporterProcessor by lazy {
        FatalIssueReporterProcessor(FatalIssueReporterStorage(destinationDirectory))
    }
    private val activityManager: ActivityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    @VisibleForTesting
    var fatalIssueReporterStatus: FatalIssueReporterStatus = buildDefaultReporterStatus()
        private set

    override fun initialize(fatalIssueMechanism: FatalIssueMechanism) {
        if (fatalIssueReporterStatus.state is FatalIssueReporterState.NotInitialized) {
            if (fatalIssueMechanism == FatalIssueMechanism.INTEGRATION) {
                fatalIssueReporterStatus = initializeIntegrationReporting()
            } else if (fatalIssueMechanism == FatalIssueMechanism.BUILT_IN) {
                fatalIssueReporterStatus = initializeBuiltInReporting()
            }
        } else {
            Log.w("capture", "Fatal issue reporting already being initialized")
        }
    }

    /**
     * Applicable when [FatalIssueMechanism.BUILT_IN] is available, given that registration
     * only occurs for calls like initialize(FatalIssueMechanism.BUILT_IN)
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        fatalIssueReporterProcessor.persistJvmCrash(
            timestamp = System.currentTimeMillis(),
            callerThread = thread,
            throwable = throwable,
        )
    }

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> =
        mapOf(
            FATAL_ISSUE_REPORTING_STATE_KEY to fatalIssueReporterStatus.state.readableType.toFieldValue(),
            FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY to
                fatalIssueReporterStatus
                    .getDuration()
                    .toFieldValue(),
        )

    private fun initializeIntegrationReporting(): FatalIssueReporterStatus =
        runCatching {
            val duration =
                measureTime {
                    verifyDirectoriesAndCopyFiles()
                }
            FatalIssueReporterStatus(
                FatalIssueReporterState.Initialized.MissingConfigFile,
                duration,
                FatalIssueMechanism.INTEGRATION,
            )
        }.getOrElse {
            FatalIssueReporterStatus(
                ProcessingFailure("Error while initializeIntegrationReporting. ${it.message}"),
                mechanism = FatalIssueMechanism.INTEGRATION,
            )
        }

    private fun initializeBuiltInReporting(): FatalIssueReporterStatus =
        runCatching {
            mainThreadHandler.runAndReturnResult {
                captureUncaughtExceptionHandler.install(this)
                persistLastExitReasonIfNeeded()
                FatalIssueReporterStatus(
                    FatalIssueReporterState.BuiltInModeInitialized,
                    mechanism = FatalIssueMechanism.INTEGRATION,
                )
            }
        }.getOrElse {
            captureUncaughtExceptionHandler.uninstall()
            FatalIssueReporterStatus(
                ProcessingFailure("Error while initializeBuiltInReporting. ${it.message}"),
                mechanism = FatalIssueMechanism.INTEGRATION,
            )
        }

    private fun persistLastExitReasonIfNeeded() {
        val lastReasonResult = latestAppExitInfoProvider.get(activityManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            lastReasonResult is LatestAppExitReasonResult.Valid
        ) {
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

    private fun buildDefaultReporterStatus(): FatalIssueReporterStatus =
        FatalIssueReporterStatus(
            FatalIssueReporterState.NotInitialized,
            mechanism = FatalIssueMechanism.NONE,
        )

    internal companion object {
        private const val CONFIGURATION_FILE_PATH = "/reports/config"
        private const val FATAL_ISSUE_REPORTING_DURATION_MILLI_KEY = "_fatal_issue_reporting_duration_ms"
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
