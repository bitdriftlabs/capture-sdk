// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.util.Log
import androidx.annotation.UiThread
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Completed
import java.io.File
import kotlin.system.measureNanoTime

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
     * @return The state of the crash reporting process
     */
    fun processCrashReportFile(): CrashReporterState {
        var crashReporterState: CrashReporterState = CrashReporterState.Initializing
        mainThreadHandler.run {
            val measuredTimeNano =
                measureNanoTime {
                    crashReporterState = verifyDirectoriesAndCopyFiles()
                }
            Log.i(
                "CAPTURE_SDK",
                "verifyDirectoriesAndCopyFiles completed with $crashReporterState and duration of $measuredTimeNano ns",
            )
        }
        return crashReporterState
    }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): CrashReporterState {
        val filesDir = appContext.filesDir.absolutePath
        val crashConfigFile = File("$filesDir$REPORTS_DIRECTORY")

        if (!crashConfigFile.exists()) {
            return Completed.MissingConfigFile("$REPORTS_DIRECTORY does not exist")
        }

        val crashConfigDetails =
            getConfigDetails(crashConfigFile) ?: let {
                return Completed.MalformedConfigFile("Malformed content at $REPORTS_DIRECTORY")
            }

        val sourcePath = "${appContext.cacheDir.absolutePath}/${crashConfigDetails.rootPath}"
        val destinationPath = "$filesDir$REPORTS_TO_BE_UPLOADED"

        return runCatching {
            copyFile(sourcePath, destinationPath, crashConfigDetails.extensionFileName)
        }.getOrElse {
            return Completed.ProcessingFailure("Couldn't process crash files", it)
        }
    }

    @UiThread
    private fun copyFile(
        sourceBaseDir: String,
        destDir: String,
        fileExtension: String,
    ): CrashReporterState {
        val source = File(sourceBaseDir)
        val destination = File(destDir).apply { if (!exists()) mkdirs() }

        if (!source.exists() || !source.isDirectory) {
            return Completed.WithoutPriorCrash("$sourceBaseDir directory does not exist or is not a directory")
        }

        val targetFile =
            getCrashFile(source, fileExtension)
                ?: let {
                    return Completed.WithoutPriorCrash("File with .$fileExtension not found in the source directory")
                }

        val destFile =
            File(destination, targetFile.name).apply { targetFile.copyTo(this, overwrite = true) }

        return if (destFile.exists()) {
            Completed.CrashReportSent("File ${targetFile.name} copied successfully")
        } else {
            Completed.WithoutPriorCrash("No prior crashes found")
        }
    }

    private fun getConfigDetails(crashConfigFile: File): ConfigDetails? =
        runCatching {
            val crashConfigDetails = crashConfigFile.readText().split(",")
            ConfigDetails(crashConfigDetails[0], crashConfigDetails[1])
        }.getOrNull()

    private fun getCrashFile(
        sourceFile: File,
        fileExtension: String,
    ): File? =
        sourceFile.walk().firstOrNull {
            it.isFile && it.extension == fileExtension
        }

    internal sealed class CrashReporterState {
        data object Initializing : CrashReporterState()

        /**
         * State indicating that crash reporting has not been initialized
         */
        data object NotInitialized : CrashReporterState()

        /**
         * Sealed class representing all completed states
         */
        sealed class Completed : CrashReporterState() {
            abstract val message: String

            /**
             * State indicating that crash report file transfer succeeded
             */
            data class CrashReportSent(
                override val message: String,
            ) : Completed()

            /**
             * State indicating that there are no prior crashes to report
             */
            data class WithoutPriorCrash(
                override val message: String,
            ) : Completed()

            /**
             * State indicating that the crash report configuration file is missing
             */
            data class MissingConfigFile(
                override val message: String,
            ) : Completed()

            /**
             * State indicating that the crash report configuration file content is incorrect
             */
            data class MalformedConfigFile(
                override val message: String,
            ) : Completed()

            /**
             * State indicating that processing crash reports failed
             */
            data class ProcessingFailure(
                override val message: String,
                val throwable: Throwable,
            ) : Completed()
        }
    }

    private data class ConfigDetails(
        val rootPath: String,
        val extensionFileName: String,
    )

    private companion object {
        private const val REPORTS_DIRECTORY = "/bitdrift_capture/reports/directories"
        private const val REPORTS_TO_BE_UPLOADED = "/bitdrift_capture/reports/new"
    }
}
