// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.Error
import io.bitdrift.capture.reports.binformat.v1.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.Frame
import io.bitdrift.capture.reports.binformat.v1.FrameType
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.SourceFile
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Process an converts an ANR report from [android.app.ApplicationExitInfo]
 * into a binary flatbuffer Report
 */
internal object AppExitAnrTraceProcessor {
    private const val ANR_MAIN_THREAD_IDENTIFIED = "\"main\""
    private const val ANR_STACKTRACE_PREFIX = "at "
    private val ANR_STACK_TRACE_REGEX = Regex("^\\s+at\\s+(.*)\\.(.*)\\((.*):(\\d+)\\)$")
    private val mainStackTraceFrames = mutableListOf<Int>()
    private var isProcessingMainThreadTrace = false

    /**
     * Process valid traceInputStream
     *
     * @return byte offset for the Report instance in the builder buffer
     */
    fun process(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        description: String?,
        traceInputStream: InputStream,
    ): Int {
        val inputStreamReader = InputStreamReader(traceInputStream)
        BufferedReader(inputStreamReader)
            .useLines { lines ->
                lines.forEach { currentLine ->
                    appendMainFramesIfNeeded(builder, currentLine)
                }
            }

        val name = description?.let { builder.createString(it) } ?: 0
        val trace = Error.createStackTraceVector(builder, mainStackTraceFrames.toIntArray())
        val reason = builder.createString(AnrReason.extractFrom(description).readableType)
        val error = Error.createError(builder, name, reason, trace, ErrorRelation.CausedBy)

        return Report.createReport(
            builder,
            sdk,
            ReportType.AppNotResponding,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, intArrayOf(error)),
            // TODO(FranAguilera): BIT-5142. Append thread info
            0,
            0,
        )
    }

    private fun isStackTraceLine(currentLine: String) = currentLine.trim().startsWith(ANR_STACKTRACE_PREFIX)

    private fun setIsMainThreadStackTrace(line: String) {
        if (line.startsWith(ANR_MAIN_THREAD_IDENTIFIED)) {
            isProcessingMainThreadTrace = true
        } else if (isProcessingMainThreadTrace && line.isEmpty()) {
            isProcessingMainThreadTrace = false
        }
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    private fun appendMainFramesIfNeeded(
        builder: FlatBufferBuilder,
        currentLine: String,
    ) {
        setIsMainThreadStackTrace(currentLine)
        if (!isStackTraceLine(currentLine)) return

        ANR_STACK_TRACE_REGEX.find(currentLine)?.destructured?.let { (className, symbolName, fileName, lineNumber) ->
            if (!isProcessingMainThreadTrace) {
                return
            }
            val path = builder.createString(fileName)
            val sourceFile =
                SourceFile.createSourceFile(
                    builder,
                    path,
                    lineNumber.toLongOrNull() ?: 0,
                    0,
                )
            val frame =
                Frame.createFrame(
                    builder,
                    FrameType.JVM,
                    builder.createString(className),
                    builder.createString(symbolName),
                    sourceFile,
                    0,
                    0u,
                    0u,
                    0,
                    0,
                    0,
                    0u,
                    false,
                    0,
                )
            mainStackTraceFrames.add(frame)
        }
    }

    /**
     * Based on android source (see frameworks/base/core/java/com/android/internal/os/TimeoutRecord.java)
     */
    private sealed class AnrReason(
        val readableType: String,
        private val sentenceToMatch: String?,
    ) {
        companion object {
            fun extractFrom(description: String?): AnrReason {
                if (description == null) return UndeterminedAnr
                val sanitizedDescription = description.lowercase()

                return when {
                    UserPerceivedAnr.matches(sanitizedDescription) -> UserPerceivedAnr
                    BackgroundAnr.matches(sanitizedDescription) -> BackgroundAnr
                    BroadcastReceiver.matches(sanitizedDescription) -> BroadcastReceiver
                    ExecutingService.matches(sanitizedDescription) -> ExecutingService
                    StartForegroundNotCalled.matches(sanitizedDescription) -> StartForegroundNotCalled
                    ContentProvider.matches(sanitizedDescription) -> ContentProvider
                    AppRegistered.matches(sanitizedDescription) -> AppRegistered
                    ShortFgsTimeout.matches(sanitizedDescription) -> ShortFgsTimeout
                    JobService.matches(sanitizedDescription) -> JobService
                    AppStart.matches(sanitizedDescription) -> AppStart
                    ServiceStart.matches(sanitizedDescription) -> ServiceStart
                    else -> UndeterminedAnr
                }
            }
        }

        fun matches(description: String) = sentenceToMatch?.let { it in description } ?: false

        /*
         * Combining all Input Dispatching Timed Out as User Perceived ANR as per public definition
         * See definition at https://developer.android.com/topic/performance/vitals/anr#android-vitals
         */
        data object UserPerceivedAnr :
            AnrReason("User Perceived ANR", "input dispatching timed out")

        data object BackgroundAnr : AnrReason("Background ANR", "bg anr")

        data object BroadcastReceiver : AnrReason("Broadcast Receiver ANR", "broadcast of intent")

        data object ExecutingService : AnrReason("Executing Service ANR", "executing service")

        data object StartForegroundNotCalled : AnrReason(
            "Service.startForeground() Not Called ANR",
            "service.startforeground() not called",
        )

        data object ContentProvider : AnrReason("Content Provider ANR", "content provider timeout")

        data object AppRegistered : AnrReason("App Registered ANR", "app registered timeout")

        data object ShortFgsTimeout :
            AnrReason("Short Foreground Service Timeout ANR", "short fgs timeout")

        data object JobService : AnrReason("Job Service ANR", "job service timeout")

        data object AppStart : AnrReason("App Start ANR", "app start timeout")

        data object ServiceStart : AnrReason("Service Start ANR", "service start timeout")

        data object UndeterminedAnr : AnrReason("Undetermined ANR", null)
    }
}
