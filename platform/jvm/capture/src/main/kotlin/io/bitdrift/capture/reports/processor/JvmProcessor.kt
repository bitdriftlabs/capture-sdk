// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.os.Build
import android.os.strictmode.Violation
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Error
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.FrameType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ThreadDetails
import io.bitdrift.capture.strictmode.StrictModeReporter

/**
 * Process JVM-related issues (crashes, violations) into a binary flatbuffer Report
 */
internal object JvmProcessor {
    /**
     * @return byte offset for the Report instance in the builder buffer
     */
    fun getJvmReport(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        throwable: Throwable,
        callerThread: Thread,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
        reportType: Byte,
        isFileSizeOptimizationEnabled: Boolean,
    ): Int {
        val stackTraceOffsetsByFrames = mutableMapOf<List<StackTraceElement>, Int>()
        val errors = buildErrors(builder, throwable, isFileSizeOptimizationEnabled)
        val threadList =
            allThreads?.map { (thread, frames) ->
                val threadId =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        thread.threadId()
                    } else {
                        @Suppress("deprecation")
                        thread.id
                    }
                val threadStack =
                    buildStackTraceVector(builder, frames, isFileSizeOptimizationEnabled, stackTraceOffsetsByFrames)
                io.bitdrift.capture.reports.binformat.v1.issue_reporting.Thread.createThread(
                    builder,
                    if (isFileSizeOptimizationEnabled) builder.createSharedString(thread.name) else builder.createString(thread.name),
                    thread == callerThread,
                    threadId.toUInt(),
                    if (isFileSizeOptimizationEnabled) {
                        builder.createSharedString(
                            thread.state.name,
                        )
                    } else {
                        builder.createString(thread.state.name)
                    },
                    thread.priority.toFloat(),
                    -1, // default value for quality of service (unused on Android)
                    threadStack,
                    summaryOffset = 0,
                )
            } ?: listOf()

        return Report.createReport(
            builder,
            sdk,
            reportType,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, errors.toIntArray()),
            ThreadDetails.createThreadDetails(
                builder,
                threadList.size.toUShort(),
                ThreadDetails.createThreadsVector(builder, threadList.toIntArray()),
            ),
            0,
            stateOffset = 0,
            featureFlagsOffset = 0,
        )
    }

    private fun buildErrors(
        builder: FlatBufferBuilder,
        throwable: Throwable,
        isFileSizeOptimizationEnabled: Boolean,
    ): List<Int> =
        generateSequence(throwable) { it.cause }
            .map { error ->
                val frames = error.stackTrace.map { getFrameDetails(builder, it, isFileSizeOptimizationEnabled) }.toIntArray()
                val className =
                    if (isFileSizeOptimizationEnabled) {
                        builder.createSharedString(
                            error.javaClass.name,
                        )
                    } else {
                        builder.createString(error.javaClass.name)
                    }
                val message =
                    error.getReason()?.let {
                        if (isFileSizeOptimizationEnabled) {
                            builder.createSharedString(it)
                        } else {
                            builder.createString(it)
                        }
                    } ?: 0
                Error.createError(
                    builder,
                    className,
                    message,
                    Error.createStackTraceVector(builder, frames),
                    ErrorRelation.CausedBy,
                )
            }.toList()

    private fun Throwable.getReason(): String? =
        if (!message.isNullOrBlank()) {
            message
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && this is Violation) {
            StrictModeReporter.getReason(this)
        } else {
            null
        }

    private fun getFrameDetails(
        builder: FlatBufferBuilder,
        element: StackTraceElement,
        isFileSizeOptimizationEnabled: Boolean,
    ): Int {
        val frameData =
            FrameData(
                className = element.className,
                symbolName = element.methodName,
                fileName = element.fileName,
                lineNumber = element.lineNumber.toLong(),
            )
        return ReportFrameBuilder.build(
            FrameType.JVM,
            builder,
            frameData,
            isFileSizeOptimizationEnabled,
        )
    }

    private fun buildStackTraceVector(
        builder: FlatBufferBuilder,
        frames: Array<StackTraceElement>,
        isFileSizeOptimizationEnabled: Boolean,
        stackTraceOffsetsByFrames: MutableMap<List<StackTraceElement>, Int>,
    ): Int {
        val frameOffsets =
            frames
                .map { frame -> getFrameDetails(builder, frame, isFileSizeOptimizationEnabled) }
                .toIntArray()

        return if (isFileSizeOptimizationEnabled) {
            stackTraceOffsetsByFrames.getOrPut(frames.asList()) {
                io.bitdrift.capture.reports.binformat.v1.issue_reporting.Thread
                    .createStackTraceVector(builder, frameOffsets)
            }
        } else {
            io.bitdrift.capture.reports.binformat.v1.issue_reporting.Thread
                .createStackTraceVector(builder, frameOffsets)
        }
    }
}
