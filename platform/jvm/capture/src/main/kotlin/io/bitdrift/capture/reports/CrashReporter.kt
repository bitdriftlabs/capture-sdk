// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.os.Build
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Initialized
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of crashes
 */
internal class CrashReporter(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) {
    private val appContext by lazy { APP_CONTEXT }

    /**
     * Process existing crash report files.
     *
     * @return The status of the crash reporting processing
     */
    fun processCrashReportFile(): CrashReporterStatus {
        var crashReporterState: CrashReporterState = CrashReporterState.Initializing
        val duration =
            measureTime {
                if (MainThreadHandler.isOnMainThread()) {
                    crashReporterState = verifyDirectoriesAndCopyFiles()
                } else {
                    mainThreadHandler.run {
                        crashReporterState = verifyDirectoriesAndCopyFiles()
                    }
                }
            }
        return CrashReporterStatus(crashReporterState, duration)
    }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): CrashReporterState {
        val filesDir = appContext.filesDir.absolutePath
        val crashConfigFile = File("$filesDir$CONFIGURATION_FILE_PATH")

        if (!crashConfigFile.exists()) {
            return Initialized.MissingConfigFile("$CONFIGURATION_FILE_PATH does not exist")
        }

        val crashConfigFileContents = crashConfigFile.readText()
        val crashConfigDetails =
            getConfigDetails(crashConfigFileContents) ?: let {
                return Initialized.MalformedConfigFile("Malformed content at $CONFIGURATION_FILE_PATH. Contents: $crashConfigFileContents")
            }

        val sourceDirectory = File("${appContext.cacheDir.absolutePath}/${crashConfigDetails.rootPath}")
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory) {
            return Initialized.WithoutPriorCrash("$sourceDirectory directory does not exist or is not a directory")
        }

        val destinationDirectory = File("$filesDir$DESTINATION_FILE_PATH").apply { if (!exists()) mkdirs() }

        return runCatching {
            findAndCopyCrashFile(
                sourceDirectory,
                destinationDirectory,
                crashConfigDetails.extensionFileName,
            )
        }.getOrElse {
            return Initialized.ProcessingFailure("Couldn't process crash files. ${it.message}")
        }
    }

    @UiThread
    private fun findAndCopyCrashFile(
        sourceDirectory: File,
        destinationDirectory: File,
        fileExtension: String,
    ): CrashReporterState {
        val crashFile =
            findCrashFile(sourceDirectory, fileExtension)
                ?: let {
                    return Initialized.WithoutPriorCrash("Crash file with .$fileExtension extension not found in the source directory")
                }

        val destinationFile = File(destinationDirectory, crashFile.toFilenameWithTimeStamp())
        crashFile.copyTo(destinationFile, overwrite = true)

        return if (destinationFile.exists()) {
            Initialized.CrashReportSent("File ${destinationFile.absolutePath} copied successfully")
        } else {
            Initialized.WithoutPriorCrash("No prior crashes found")
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
        return fileCreationEpochTime.toString() + "." + this.extension
    }

    private fun getFileCreationTimeEpochInMillis(file: File): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fileTime =
                Files.getAttribute(file.toPath(), FILE_CREATION_TIME_ATTRIBUTE) as FileTime
            fileTime.toMillis()
        } else {
            file.lastModified()
        }

    internal sealed class CrashReporterState(
        open val readableType: String,
    ) {
        /**
         * Contains details of the state
         */
        abstract val message: String

        /**
         * Indicates that initial setup call is in progress
         */
        data object Initializing : CrashReporterState("INITIALIZING") {
            override val message: String
                get() = "Initializing Crash Reporting"
        }

        /**
         * State indicating that crash reporting has not been initialized
         */
        data object NotInitialized : CrashReporterState("NOT_INITIALIZED") {
            override val message: String
                get() = "Crash reporting not initialized"
        }

        /**
         * Sealed class representing all initialized states
         */
        sealed class Initialized(
            override val readableType: String,
            override val message: String,
        ) : CrashReporterState(readableType) {
            /**
             * State indicating that prior crash report was sent
             */
            data class CrashReportSent(
                override val message: String,
            ) : Initialized("CRASH_REPORT_SENT", message)

            /**
             * State indicating that there are no prior crashes to report
             */
            data class WithoutPriorCrash(
                override val message: String,
            ) : Initialized("NO_PRIOR_CRASHES", message)

            /**
             * State indicating that the crash report configuration file is missing
             */
            data class MissingConfigFile(
                override val message: String,
            ) : Initialized("MISSING_CRASH_CONFIG_FILE", message)

            /**
             * State indicating that the crash report configuration file content is incorrect
             */
            data class MalformedConfigFile(
                override val message: String,
            ) : Initialized("MALFORMED_CRASH_CONFIG_FILE", message)

            /**
             * State indicating that processing crash reports failed
             */
            data class ProcessingFailure(
                override val message: String,
            ) : Initialized("CRASH_PROCESSING_FAILURE", message)
        }
    }

    data class CrashReporterStatus(
        val state: CrashReporterState,
        val duration: Duration? = null,
    )

    private data class ConfigDetails(
        val rootPath: String,
        val extensionFileName: String,
    )

    internal companion object {
        // TODO(FranAguilera): To rename to /bitdrift_capture/reports/config when shared-core is bumped
        private const val CONFIGURATION_FILE_PATH = "/bitdrift_capture/reports/directories"
        private const val DESTINATION_FILE_PATH = "/bitdrift_capture/reports/new"
        private const val FILE_CREATION_TIME_ATTRIBUTE = "creationTime"
        private const val CRASH_REPORTING_STATE_KEY = "crash_reporting_state"
        private const val CRASH_REPORTING_DETAILS_KEY = "crash_reporting_details"
        private const val CRASH_REPORTING_DURATION_NANO_KEY = "crash_reporting_duration_nanos"

        fun CrashReporterStatus.buildFieldsMap(): Map<String, FieldValue> =
            buildMap {
                put(CRASH_REPORTING_STATE_KEY, state.readableType.toFieldValue())
                put(CRASH_REPORTING_DETAILS_KEY, state.message.toFieldValue())
                put(CRASH_REPORTING_DURATION_NANO_KEY, getDurationFieldValue().toFieldValue())
            }

        @VisibleForTesting
        fun CrashReporterStatus.getDurationFieldValue(): String = duration?.toString(DurationUnit.NANOSECONDS) ?: "n/a"
    }
}
