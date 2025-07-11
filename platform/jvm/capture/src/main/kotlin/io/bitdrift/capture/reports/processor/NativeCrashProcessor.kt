// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos.Tombstone
import io.bitdrift.capture.reports.binformat.v1.Error
import io.bitdrift.capture.reports.binformat.v1.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.FrameType
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.Thread
import io.bitdrift.capture.reports.binformat.v1.ThreadDetails
import java.io.InputStream

/**
 * Parses native tombstone logs from ApplicationExitInfo (REASON_CRASH_NATIVE)
 * and converts to a FlatBuffer Report.
 */
internal object NativeCrashProcessor {
    fun process(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        description: String?,
        traceInputStream: InputStream,
    ): Int {
        val tombstone = Tombstone.parseFrom(traceInputStream)
        val nativeErrors = mutableListOf<Int>()
        val threadOffsets = mutableListOf<Int>()

        tombstone.threadsMap.forEach { (tid, thread) ->
            val frameOffsets =
                thread.currentBacktraceList
                    .map { frame ->
                        val frameAddress = frame.pc.toULong()
                        val functionOffset = frame.functionOffset.toULong()
                        val frameData =
                            FrameData(
                                symbolName = frame.functionName,
                                fileName = frame.fileName,
                                imageId = frame.buildId,
                                frameAddress = frame.pc.toULong(),
                                symbolAddress = frameAddress - functionOffset,
                            )
                        ReportFrameBuilder.build(FrameType.AndroidNative, builder, frameData)
                    }.toIntArray()

            val threadOffset =
                Thread.createThread(
                    builder,
                    builder.createString(thread.name.ifEmpty { "native-thread-${thread.id}" }),
                    isCrashingThread(tid, tombstone),
                    thread.id.toUInt(),
                    builder.createString(""),
                    0f,
                    0,
                    Thread.createStackTraceVector(builder, frameOffsets),
                )
            threadOffsets.add(threadOffset)

            if (isCrashingThread(tid, tombstone)) {
                nativeErrors.add(createErrorOffset(builder, description, tombstone, frameOffsets))
            }
        }

        val threadDetailsOffset =
            ThreadDetails.createThreadDetails(
                builder,
                threadOffsets.size.toUShort(),
                ThreadDetails.createThreadsVector(builder, threadOffsets.toIntArray()),
            )

        return Report.createReport(
            builder,
            sdk,
            ReportType.NativeCrash,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, nativeErrors.toIntArray()),
            threadDetailsOffset,
            0,
        )
    }

    private fun createErrorOffset(
        builder: FlatBufferBuilder,
        description: String?,
        tombstone: Tombstone,
        frameOffsets: IntArray,
    ): Int {
        val reason =
            builder.createString(tombstone.signalInfo.name.ifEmpty { description })
        val causeText =
            tombstone.causesList.firstOrNull()?.humanReadable
                ?: tombstone.abortMessage.ifEmpty { "Native crash" }
        val cause = builder.createString(causeText)
        return Error.createError(
            builder,
            reason,
            cause,
            Error.createStackTraceVector(builder, frameOffsets),
            ErrorRelation.CausedBy,
        )
    }

    private fun isCrashingThread(
        threadId: Int,
        tombstone: Tombstone,
    ): Boolean = threadId == tombstone.tid
}
