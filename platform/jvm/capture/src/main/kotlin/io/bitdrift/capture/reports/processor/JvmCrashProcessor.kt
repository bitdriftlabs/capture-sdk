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
import io.bitdrift.capture.reports.binformat.v1.ThreadDetails

/**
 * Process crash into a binary flatbuffer Report
 */
internal object JvmCrashProcessor {
    /**
     * @return byte offset for the Report instance in the builder buffer
     */
    fun getJvmCrashReport(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        throwable: Throwable,
        callerThread: Thread,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    ): Int {
        val trace = throwable.stackTrace.map { e -> getFrameDetails(builder, e) }.toIntArray()
        val message = throwable.message?.let { builder.createString(it) } ?: 0
        val error =
            Error.createError(
                builder,
                builder.createString(throwable.javaClass.name),
                message,
                Error.createStackTraceVector(builder, trace),
                ErrorRelation.CausedBy,
            )
        val threadList =
            allThreads?.map { (thread, frames) ->
                val threadStack = frames.map { e -> getFrameDetails(builder, e) }.toIntArray()
                io.bitdrift.capture.reports.binformat.v1.Thread.createThread(
                    builder,
                    builder.createString(thread.name),
                    thread == callerThread,
                    thread.id.toUInt(),
                    builder.createString(thread.state.name),
                    thread.priority.toFloat(),
                    -1, // default value for quality of service (unused on Android)
                    io.bitdrift.capture.reports.binformat.v1.Thread
                        .createStackTraceVector(builder, threadStack),
                )
            } ?: listOf()
        return Report.createReport(
            builder,
            sdk,
            ReportType.JVMCrash,
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

    private fun getFrameDetails(
        builder: FlatBufferBuilder,
        element: StackTraceElement,
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
        )
    }
}
