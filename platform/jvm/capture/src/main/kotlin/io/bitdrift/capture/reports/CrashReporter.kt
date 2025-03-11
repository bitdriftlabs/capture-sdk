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
import io.bitdrift.capture.reports.CrashReporterState.Initialized.ProcessingFailure
import io.bitdrift.capture.utils.SdkDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Handles internal reporting of crashes
 */
internal class CrashReporter(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) {
    private val appContext = APP_CONTEXT

    /**
     * Process existing crash report files.
     *
     * @return The status of the crash reporting processing
     */
    fun processCrashReportFile(): CrashReporterStatus =
        runCatching {
            if (MainThreadHandler.isOnMainThread()) {
                runVerifyDirectoriesAndCopyFiles()
            } else {
                mainThreadHandler.runAndReturnResult {
                    runVerifyDirectoriesAndCopyFiles()
                }
            }
        }.getOrElse {
            CrashReporterStatus(ProcessingFailure("Error while processCrashReportFile. ${it.message}"))
        }

    @UiThread
    private fun runVerifyDirectoriesAndCopyFiles(): CrashReporterStatus {
        var crashReporterState: CrashReporterState
        val duration =
            measureTime {
                crashReporterState = verifyDirectoriesAndCopyFiles()
            }
        return CrashReporterStatus(crashReporterState, duration)
    }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): CrashReporterState {
        val sdkDirectory = SdkDirectory.getPath(appContext)
        val crashConfigFile = File(sdkDirectory, CONFIGURATION_FILE_PATH)

        if (!crashConfigFile.exists()) {
            return CrashReporterState.Initialized.MissingConfigFile
        }

        val crashConfigFileContents = crashConfigFile.readText()
        val crashConfigDetails =
            getConfigDetails(crashConfigFileContents) ?: let {
                return CrashReporterState.Initialized.MalformedConfigFile
            }

        val sourceDirectory =
            File(appContext.cacheDir.absolutePath, crashConfigDetails.rootPath)
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory) {
            return CrashReporterState.Initialized.WithoutPriorCrash
        }

        val destinationDirectory =
            File(sdkDirectory, DESTINATION_FILE_PATH).apply { if (!exists()) mkdirs() }

        return runCatching {
            findAndCopyCrashFile(
                sourceDirectory,
                destinationDirectory,
                crashConfigDetails.extensionFileName,
            )
        }.getOrElse {
            return ProcessingFailure("Couldn't process crash files. ${it.message}")
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
                    return CrashReporterState.Initialized.WithoutPriorCrash
                }

        verifyDirectoryIsEmpty(destinationDirectory)
        val destinationFile = File(destinationDirectory, crashFile.toFilenameWithTimeStamp())
        crashFile.copyTo(destinationFile, overwrite = true)

        return if (destinationFile.exists()) {
            CrashReporterState.Initialized.CrashReportSent
        } else {
            CrashReporterState.Initialized.WithoutPriorCrash
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

    private data class ConfigDetails(
        val rootPath: String,
        val extensionFileName: String,
    )

    internal companion object {
        // TODO(FranAguilera): To rename to /bitdrift_capture/reports/config when shared-core is bumped
        private const val CONFIGURATION_FILE_PATH = "/reports/directories"
        private const val DESTINATION_FILE_PATH = "/reports/new"
        private const val LAST_MODIFIED_TIME_ATTRIBUTE = "lastModifiedTime"
        private const val CRASH_REPORTING_STATE_KEY = "_crash_reporting_state"
        private const val CRASH_REPORTING_DURATION_MILLI_KEY = "_crash_reporting_duration_ms"

        /**
         * Returns the fields map with latest [CrashReporterStatus]
         */
        internal fun CrashReporterStatus.buildFieldsMap(): Map<String, FieldValue> =
            buildMap {
                put(CRASH_REPORTING_STATE_KEY, state.readableType.toFieldValue())
                put(CRASH_REPORTING_DURATION_MILLI_KEY, getDuration().toFieldValue())
            }

        @VisibleForTesting
        fun CrashReporterStatus.getDuration(): String = duration?.toDouble(DurationUnit.MILLISECONDS)?.toString() ?: "n/a"
    }
}
