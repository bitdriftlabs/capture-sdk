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
import io.bitdrift.capture.reports.CrashReporter.CrashReportingState.Completed
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
    fun processCrashReportFile(): CrashReportingState {
        var crashReportingState: CrashReportingState = CrashReportingState.Initializing
        val measuredTimeNano =
            measureNanoTime {
                if (MainThreadHandler.isOnMainThread()) {
                    crashReportingState = verifyDirectoriesAndCopyFiles()
                } else {
                    mainThreadHandler.run {
                        crashReportingState = verifyDirectoriesAndCopyFiles()
                    }
                }
            }
        Log.i(
            "CAPTURE_SDK",
            "verifyDirectoriesAndCopyFiles completed with $crashReportingState and duration of $measuredTimeNano ns",
        )
        return crashReportingState
    }

    @UiThread
    private fun verifyDirectoriesAndCopyFiles(): CrashReportingState {
        val filesDir = appContext.filesDir.absolutePath
        val crashConfigFile = File("$filesDir$REPORTS_DIRECTORY")

        if (!crashConfigFile.exists()) {
            return Completed.MissingConfigFile("$REPORTS_DIRECTORY does not exist")
        }

        val crashConfigDetails =
            crashConfigFile.readText().split(",").takeIf { it.size >= MIN_EXPECTED_CSV_FILE }
                ?: let {
                    return Completed.ProcessingFailure(
                        "Malformed content at $REPORTS_DIRECTORY",
                        IllegalStateException(),
                    )
                }

        val (sourceFilePath, fileExtension) = crashConfigDetails[0] to crashConfigDetails[1]
        val sourcePath = "${appContext.cacheDir.absolutePath}/$sourceFilePath"
        val destinationPath = "$filesDir$REPORTS_TO_BE_UPLOADED"

        return runCatching {
            copyFile(sourcePath, destinationPath, fileExtension)
        }.getOrElse {
            return Completed.ProcessingFailure("Couldn't process crash files", it)
        }
    }

    @UiThread
    private fun copyFile(
        sourceBaseDir: String,
        destDir: String,
        fileExtension: String,
    ): CrashReportingState {
        val source = File(sourceBaseDir)
        val destination = File(destDir).apply { if (!exists()) mkdirs() }

        if (!source.exists() || !source.isDirectory) {
            return Completed.WithoutPriorCrash("$sourceBaseDir directory does not exist or is not a directory")
        }

        val targetFile =
            source.walk().firstOrNull { it.isFile && it.extension == fileExtension }
                ?: let {
                    return Completed.WithoutPriorCrash("File with .$fileExtension not found in the source directory")
                }

        val destFile = File(destination, targetFile.name).apply { targetFile.copyTo(this, overwrite = true) }
        return if (destFile.exists()) {
            Completed.CrashReportSent("File copied successfully: ${targetFile.name}")
        } else {
            Completed.WithoutPriorCrash("No prior crashes found")
        }
    }

    internal sealed class CrashReportingState {
        data object Initializing : CrashReportingState()

        /**
         * State indicating that crash reporting has not been initialized
         */
        data object NotInitialized : CrashReportingState()

        /**
         * Sealed class representing all completed states
         */
        sealed class Completed : CrashReportingState() {
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
             * State indicating that processing crash reports failed
             */
            data class ProcessingFailure(
                override val message: String,
                val throwable: Throwable,
            ) : Completed()
        }
    }

    private companion object {
        private const val REPORTS_DIRECTORY = "/bitdrift_capture/reports/directories"
        private const val REPORTS_TO_BE_UPLOADED = "/bitdrift_capture/reports/new"
        private const val MIN_EXPECTED_CSV_FILE = 2
    }
}
