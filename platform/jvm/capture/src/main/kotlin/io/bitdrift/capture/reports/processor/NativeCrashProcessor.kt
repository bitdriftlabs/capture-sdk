package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.TombstoneProtos.Tombstone
import com.google.flatbuffers.FlatBufferBuilder
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
                        val frameData =
                            FrameData(
                                className = frame.fileName,
                                symbolName = frame.functionName,
                                fileName = frame.fileName,
                                lineNumber = frame.functionOffset,
                            )
                        ReportFrameBuilder.build(FrameType.AndroidNative, builder, frameData)
                    }.toIntArray()

            val threadOffset =
                Thread.createThread(
                    builder,
                    builder.createString(thread.name.ifEmpty { "native-thread-${thread.id}" }),
                    isCrashingThread(tid, tombstone),
                    thread.id.toUInt(),
                    builder.createString(thread.name.ifEmpty { "UNKNOWN" }),
                    0f,
                    0,
                    Thread.createStackTraceVector(builder, frameOffsets),
                )
            threadOffsets.add(threadOffset)

            if (isCrashingThread(tid, tombstone)) {
                val reason = builder.createString(tombstone.signalInfo.name.ifEmpty { description })
                val cause =
                    tombstone.causesList
                        .firstOrNull()
                        ?.humanReadable
                        ?.let { builder.createString(it) }
                        ?: builder.createString(tombstone.abortMessage.ifEmpty { "Native crash" })

                val errorOffset =
                    Error.createError(
                        builder,
                        reason,
                        cause,
                        Error.createStackTraceVector(builder, frameOffsets),
                        ErrorRelation.CausedBy,
                    )
                nativeErrors.add(errorOffset)
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

    private fun isCrashingThread(
        threadId: Int,
        tombstone: Tombstone,
    ): Boolean = threadId == tombstone.tid
}
