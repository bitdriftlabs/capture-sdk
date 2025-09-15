// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos
import io.bitdrift.capture.TombstoneProtos.Tombstone
import io.bitdrift.capture.reports.binformat.v1.BinaryImage
import io.bitdrift.capture.reports.binformat.v1.Error
import io.bitdrift.capture.reports.binformat.v1.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.FrameType
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.Thread
import io.bitdrift.capture.reports.binformat.v1.ThreadDetails
import okhttp3.internal.toHexString
import java.io.InputStream

/**
 * Parses native tombstone logs from ApplicationExitInfo (REASON_CRASH_NATIVE)
 * and converts to a FlatBuffer Report.
 */
internal object NativeCrashProcessor {
    private val signalDescriptions =
        mapOf(
            "SIGABRT" to "Abort program",
            "SIGBUS" to "Bus error (bad memory access)",
            "SIGFPE" to "Floating‑point exception",
            "SIGILL" to "Illegal instruction",
            "SIGSEGV" to "Segmentation violation (invalid memory reference)",
            "SIGTRAP" to "Trace/breakpoint trap",
        )

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
        val binaryImageOffsets = mutableListOf<Int>()

        val referencedMappings = mutableSetOf<TombstoneProtos.MemoryMapping>()

        tombstone.threadsMap.forEach { (tid, thread) ->
            val frameOffsets =
                thread.currentBacktraceList
                    .map { frame ->
                        // Attempt to retrieve the binary image based on the program counter of the
                        // current frame. Use a binary search as the list of mappings can be somewhat large.
                        val binaryImageIndex =
                            tombstone.memoryMappingsList.binarySearch {
                                when {
                                    frame.pc < it.beginAddress -> 1 // look left
                                    frame.pc >= it.endAddress -> -1 // look right
                                    else -> 0
                                }
                            }

                        val imageId =
                            if (binaryImageIndex >= 0) {
                                val binaryImage = tombstone.memoryMappingsList[binaryImageIndex]
                                referencedMappings.add(binaryImage)
                                binaryImage.buildId
                                    .ifBlank { binaryImage.mappingName }
                                    .ifBlank { binaryImage.anonymousImageId() }
                            } else {
                                // This shouldn't really happen but if it does at least keep the filename as reported
                                // on the tombstone for debugging purposes.
                                frame.fileName
                            }

                        val frameData =
                            FrameData(
                                symbolName = frame.functionName,
                                fileName = null,
                                imageId = imageId,
                                frameAddress = frame.pc.toULong(),
                            )
                        ReportFrameBuilder.build(
                            FrameType.AndroidNative,
                            builder,
                            frameData,
                        )
                    }.toIntArray()

            val threadOffset =
                Thread.createThread(
                    builder,
                    builder.createString(thread.name.ifEmpty { "native-thread-${thread.id}" }),
                    isCrashingThread(tid, tombstone),
                    thread.id.toUInt(),
                    0,
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

        referencedMappings.forEach {
            binaryImageOffsets.add(
                BinaryImage.createBinaryImage(
                    builder,
                    builder.createString(
                        it.buildId
                            .ifBlank { it.mappingName }
                            .ifBlank { it.anonymousImageId() },
                    ),
                    builder.createString(it.mappingName.ifBlank { it.anonymousImageId() }),
                    it.beginAddress.toULong(),
                ),
            )
        }
        return Report.createReport(
            builder,
            sdk,
            ReportType.NativeCrash,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, nativeErrors.toIntArray()),
            threadDetailsOffset,
            Report.createBinaryImagesVector(builder, binaryImageOffsets.toIntArray()),
        )
    }

    private fun createErrorOffset(
        builder: FlatBufferBuilder,
        description: String?,
        tombstone: Tombstone,
        frameOffsets: IntArray,
    ): Int {
        val signalName = tombstone.signalInfo.name
        val reason = builder.createString(signalName.ifEmpty { description })
        val causeText =
            tombstone.causesList.firstOrNull()?.humanReadable ?: tombstone.abortMessage.ifEmpty {
                signalDescriptions[signalName] ?: "Native crash"
            }
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

/** Returns a generated name for a MemoryMapping without an explicit name. This mimics the behavior
of the Android tombstone parser.
 */
fun TombstoneProtos.MemoryMapping.anonymousImageId(): String = "<anonymous:${beginAddress.toHexString()}>"
