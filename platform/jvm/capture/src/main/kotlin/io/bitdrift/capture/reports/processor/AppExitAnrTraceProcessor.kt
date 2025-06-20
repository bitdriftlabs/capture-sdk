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
import io.bitdrift.capture.reports.binformat.v1.Thread
import io.bitdrift.capture.reports.binformat.v1.ThreadDetails
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Process an converts an ANR report from [android.app.ApplicationExitInfo]
 * into a binary flatbuffer Report
 */
internal object AppExitAnrTraceProcessor {
    private val ACTIVE_THREAD_STATES = setOf("Runnable", "Native")
    private const val ANR_MAIN_THREAD_IDENTIFIER = "\"main\""
    private val ANR_STACK_TRACE_REGEX = Regex("^\\s+at\\s+(.*)\\.(.*)\\((.*):(\\d+)\\)$")
    private const val ADDITIONAL_THREAD_INFO_IDENTIFIER = "|"
    private const val ENDING_THREADS_PART_IDENTIFIER = "Zygote loaded classes"
    private val THREAD_DETAILS_REGEX = Regex("^\"([^\"]+)\"(?:\\s+daemon)?\\s+prio=(\\d+)\\s+tid=(\\d+)(.*)$")

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
        val stackTraceAndThreadInfo = extractStackTraceAndThreadInfo(builder, inputStreamReader)

        val reason = builder.createString(AnrReason.extractFrom(description).readableType)
        val detail = description?.let { builder.createString(it) } ?: 0
        val error =
            Error.createError(
                builder,
                reason,
                detail,
                stackTraceAndThreadInfo.capturedErrorTrace,
                ErrorRelation.CausedBy,
            )
        val threadList = stackTraceAndThreadInfo.threadList
        return Report.createReport(
            builder,
            sdk,
            ReportType.AppNotResponding,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, intArrayOf(error)),
            ThreadDetails.createThreadDetails(
                builder,
                threadList.size.toUShort(),
                ThreadDetails.createThreadsVector(builder, threadList.toIntArray()),
            ),
            0,
        )
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    private fun extractStackTraceAndThreadInfo(
        builder: FlatBufferBuilder,
        inputStreamReader: InputStreamReader,
    ): StackTraceAndThreadInfo {
        var capturedErrorTrace = 0
        val threadList = mutableListOf<Int>()

        BufferedReader(inputStreamReader).useLines { lines ->

            val currentFrames = mutableListOf<Int>()
            var currentThreadData: ThreadData? = null
            var isRelevantTracePart = false

            lines.forEach { line ->

                when {
                    isStartOfRelevantTracePart(line, isRelevantTracePart) ->
                        isRelevantTracePart = true

                    isEndOfRelevantTracePart(line) -> return@forEach

                    isValidFrameLine(line, isRelevantTracePart) -> {
                        val threadDetailsMatchResult = THREAD_DETAILS_REGEX.find(line)
                        if (threadDetailsMatchResult != null) {
                            val (name, priority, tid, state) = threadDetailsMatchResult.destructured
                            currentThreadData =
                                ThreadData(
                                    name = builder.createString(name),
                                    state = builder.createString(state.trim()),
                                    isActive = isThreadActive(state),
                                    priority = priority.toFloatOrNull() ?: 0F,
                                    tid = tid.toUIntOrNull() ?: 0U,
                                )
                        } else {
                            currentFrames.add(line.toFrameData(builder))
                        }
                    }

                    isEndOfCurrentThreadBlock(line, isRelevantTracePart) -> {
                        if (currentFrames.isNotEmpty()) {
                            // First trace will be labelled as Captured Error
                            if (capturedErrorTrace == 0) {
                                capturedErrorTrace =
                                    Error.createStackTraceVector(
                                        builder,
                                        currentFrames.toIntArray(),
                                    )
                            } else {
                                currentThreadData?.let {
                                    threadList.add(buildThread(builder, it, currentFrames))
                                }
                            }
                        }
                        currentFrames.clear()
                        currentThreadData = null
                    }
                }
            }
        }
        return StackTraceAndThreadInfo(capturedErrorTrace, threadList)
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    private fun String.toFrameData(builder: FlatBufferBuilder): Int =
        ANR_STACK_TRACE_REGEX
            .find(this)
            ?.destructured
            ?.let { (className, symbolName, fileName, lineNumber) ->
                ReportFrameBuilder.build(
                    FrameType.JVM,
                    builder,
                    FrameData(
                        className = className,
                        symbolName = symbolName,
                        fileName = fileName,
                        lineNumber = lineNumber.toLongOrNull(),
                    ),
                )
            } ?: ReportFrameBuilder.build(
            FrameType.JVM,
            builder,
            FrameData(
                // TODO(FranAguilera): BIT-5576. To update underlying Report model to support a Frame Entry for
                //  details (e..g. sleeping on <0x07849c2b> (a java.lang.Object))
                className = this,
            ),
        )

    private fun isEndOfRelevantTracePart(line: String): Boolean = line.contains(ENDING_THREADS_PART_IDENTIFIER)

    private fun isStartOfRelevantTracePart(
        line: String,
        isRelevantTracePart: Boolean,
    ): Boolean = line.contains(ANR_MAIN_THREAD_IDENTIFIER) && !isRelevantTracePart

    private fun isEndOfCurrentThreadBlock(
        line: String,
        isRelevantTracePart: Boolean,
    ): Boolean = isRelevantTracePart && line.isEmpty()

    private fun isValidFrameLine(
        line: String,
        isRelevantTracePart: Boolean,
    ): Boolean = isRelevantTracePart && line.isNotEmpty() && !line.contains(ADDITIONAL_THREAD_INFO_IDENTIFIER)

    private fun isThreadActive(threadState: String): Boolean = ACTIVE_THREAD_STATES.any { threadState.contains(it, ignoreCase = true) }

    private fun buildThread(
        builder: FlatBufferBuilder,
        threadData: ThreadData,
        currentFrames: List<Int>,
    ): Int =
        Thread.createThread(
            builder,
            threadData.name,
            threadData.isActive,
            threadData.tid,
            threadData.state,
            threadData.priority,
            -1,
            Thread.createStackTraceVector(
                builder,
                currentFrames.toIntArray(),
            ),
        )

    private data class ThreadData(
        val name: Int,
        val state: Int,
        val isActive: Boolean,
        val priority: Float,
        val tid: UInt,
    )

    private data class StackTraceAndThreadInfo(
        val capturedErrorTrace: Int,
        val threadList: List<Int>,
    )

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
