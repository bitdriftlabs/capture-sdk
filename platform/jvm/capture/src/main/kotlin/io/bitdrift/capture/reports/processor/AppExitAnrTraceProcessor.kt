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
import io.bitdrift.capture.reports.binformat.v1.FrameType
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
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
    private const val ADDITIONAL_THREAD_INFO_IDENTIFIER = "|"
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

        val trace = Error.createStackTraceVector(builder, mainStackTraceFrames.toIntArray())
        val reason = builder.createString(AnrReason.extractFrom(description).readableType)
        val detail = description?.let { builder.createString(it) } ?: 0
        val error = Error.createError(builder, reason, detail, trace, ErrorRelation.CausedBy)

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

        if (!isProcessingMainThreadTrace) {
            return
        }

        val frame =
            when {
                isStackTraceLine(currentLine) -> {
                    ANR_STACK_TRACE_REGEX
                        .find(currentLine)
                        ?.destructured
                        ?.let { (className, symbolName, fileName, lineNumber) ->
                            val frameData =
                                FrameData(
                                    className = className,
                                    symbolName = symbolName,
                                    fileName = fileName,
                                    lineNumber = lineNumber.toLongOrNull(),
                                )
                            ReportFrameBuilder.build(
                                FrameType.JVM,
                                builder,
                                frameData,
                            )
                        }
                }

                isAdditionalStackTraceInfo(currentLine) -> {
                    // TODO(FranAguilera): BIT-5576. To update underlying Report model to support a Frame Entry for
                    //  details (e..g. sleeping on <0x07849c2b> (a java.lang.Object))
                    ReportFrameBuilder.build(
                        FrameType.JVM,
                        builder,
                        FrameData(className = currentLine),
                    )
                }

                else -> null
            }
        frame?.let { mainStackTraceFrames.add(it) }
    }

    private fun isAdditionalStackTraceInfo(currentLine: String): Boolean = !currentLine.contains(ADDITIONAL_THREAD_INFO_IDENTIFIER)

    /**
     * Based on android source (see frameworks/base/core/java/com/android/internal/os/TimeoutRecord.java)
     */
    private sealed class AnrReason(
        val readableType: String,
        private val sentenceToMatch: String? = null,
    ) {
        companion object {
            fun extractFrom(description: String?): AnrReason {
                if (description == null) return UndeterminedAnr
                val sanitizedDescription = description.lowercase()

                return when {
                    UserPerceivedAnr.matches(sanitizedDescription) -> UserPerceivedAnr
                    BackgroundAnr.matches(sanitizedDescription) -> BackgroundAnr
                    BroadcastReceiver.matches(sanitizedDescription) -> BroadcastReceiver
                    ServiceAnr.matches(sanitizedDescription) -> ServiceAnr
                    ContentProvider.matches(sanitizedDescription) -> ContentProvider
                    AppRegistered.matches(sanitizedDescription) -> AppRegistered
                    AppStart.matches(sanitizedDescription) -> AppStart
                    else -> UndeterminedAnr
                }
            }
        }

        open fun matches(description: String) = sentenceToMatch?.let { it in description } ?: false

        /*
         * Even though play console definition at https://developer.android.com/topic/performance/vitals/anr#android-vitals
         * mentions that User Perceived ANRs only are tracked as Input Dispatching time out,
         * there are ANRs perceived by the user with a description of just `user request after error`,
         * this occurs when the user sees the native ANR dialog
         */
        data object UserPerceivedAnr :
            AnrReason("User Perceived ANR") {
            override fun matches(description: String): Boolean =
                "input dispatching timed out" in description ||
                    "user request after error" == description
        }

        data object BackgroundAnr : AnrReason("Background ANR", "bg anr")

        data object BroadcastReceiver : AnrReason("Broadcast Receiver ANR", "broadcast of intent")

        data object ServiceAnr : AnrReason("Service ANR") {
            private val serviceMessageTypes =
                listOf(
                    "executing service",
                    "service.startforeground() not called",
                    "short fgs timeout",
                    "timed out while trying to bind",
                    "job service timeout",
                    "no response to onstopjob",
                    "service start timeout",
                )

            override fun matches(description: String): Boolean = serviceMessageTypes.any { it in description }
        }

        data object ContentProvider : AnrReason("Content Provider ANR", "content provider timeout")

        data object AppRegistered : AnrReason("App Registered ANR", "app registered timeout")

        data object AppStart : AnrReason("App Start ANR", "app start timeout")

        data object UndeterminedAnr : AnrReason("Undetermined ANR")
    }
}
