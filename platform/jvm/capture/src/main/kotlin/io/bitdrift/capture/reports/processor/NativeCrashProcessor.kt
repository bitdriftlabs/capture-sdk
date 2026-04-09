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
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.BinaryImage
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Error
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.FrameType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Thread
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ThreadDetails
import java.io.InputStream

/**
 * Parses native tombstone logs from ApplicationExitInfo (REASON_CRASH_NATIVE)
 * and converts to a FlatBuffer Report.
 */
internal object NativeCrashProcessor {
    // For REASON_CRASH_NATIVE, Android stores the terminating signal number in
    // ApplicationExitInfo.status after decoding the raw process exit status via WTERMSIG.
    private const val SIGNAL_ABORT = 6
    private const val SIGNAL_BUS_ERROR = 7
    private const val SIGNAL_FLOATING_POINT_EXCEPTION = 8
    private const val SIGNAL_ILLEGAL_INSTRUCTION = 4
    private const val SIGNAL_SEGMENTATION_VIOLATION = 11
    private const val SIGNAL_TRACE_TRAP = 5

    private val signalsByNumber =
        mapOf(
            SIGNAL_ABORT to SignalInfo("SIGABRT", "Abort program"),
            SIGNAL_BUS_ERROR to SignalInfo("SIGBUS", "Bus error (bad memory access)"),
            SIGNAL_FLOATING_POINT_EXCEPTION to SignalInfo("SIGFPE", "Floating-point exception"),
            SIGNAL_ILLEGAL_INSTRUCTION to SignalInfo("SIGILL", "Illegal instruction"),
            SIGNAL_SEGMENTATION_VIOLATION to SignalInfo("SIGSEGV", "Segmentation violation (invalid memory reference)"),
            SIGNAL_TRACE_TRAP to SignalInfo("SIGTRAP", "Trace/breakpoint trap"),
        )

    fun process(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        description: String?,
        traceInputStream: InputStream?,
        terminatingSignalNumber: Int,
    ): Int {
        if (traceInputStream == null) {
            return createReportWithoutTraces(builder, sdk, appMetrics, deviceMetrics, description, terminatingSignalNumber)
        }

        val tombstone = Tombstone.parseFrom(traceInputStream)
        val nativeErrors = mutableListOf<Int>()
        val threadOffsets = mutableListOf<Int>()
        val binaryImageOffsets = mutableListOf<Int>()

        val referencedMappings = mutableSetOf<TombstoneProtos.MemoryMapping>()

        tombstone.threadsMap.forEach { (tid, thread) ->
            val frameOffsets =
                thread.currentBacktraceList
                    .map { frame ->
                        // The tombstone doesn't tell us the load offset of the image directly, so compute it using
                        // what we got.
                        val imageLoadOffset = frame.pc - frame.relPc

                        // Since the memory maps list can be fairly long, we rely on it being sorted and use a binary search
                        // to look for a candidate.
                        val binaryImageIndex =
                            tombstone.memoryMappingsList.binarySearchBy(imageLoadOffset) { it.beginAddress }
                        val binaryImage =
                            tombstone.memoryMappingsList.getOrNull(binaryImageIndex)

                        val imageId =
                            if (binaryImage != null) {
                                referencedMappings.add(binaryImage)
                                binaryImage.imageId()
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
                    summaryOffset = 0,
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
                    builder.createString(it.imageId()),
                    builder.createString(it.path()),
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
            stateOffset = 0,
            featureFlagsOffset = 0,
        )
    }

    /**
     * Creates a minimal native crash report for cases where tombstone data is unavailable
     * (e.g. native crashes on API level 30 where [android.app.ApplicationExitInfo.getTraceInputStream]
     * returns null). The report includes the crash description and uses empty stack traces,
     * empty thread details, and empty binary images. When a [terminatingSignalNumber] is available
     * (from [android.app.ApplicationExitInfo.getStatus], which Android already derives from
     * the process exit status via WTERMSIG for native crashes), the signal name and description
     * are used for the error name and reason fields.
     */
    private fun createReportWithoutTraces(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        description: String?,
        terminatingSignalNumber: Int,
    ): Int {
        val defaultReason = "Native crash"
        val signalInfo = signalsByNumber[terminatingSignalNumber]
        val name = builder.createString(signalInfo?.name ?: description ?: defaultReason)
        val reason = builder.createString(signalInfo?.description ?: defaultReason)
        val errorOffset =
            Error.createError(
                builder,
                name,
                reason,
                Error.createStackTraceVector(builder, intArrayOf()),
                ErrorRelation.CausedBy,
            )
        val threadDetailsOffset =
            ThreadDetails.createThreadDetails(
                builder,
                0.toUShort(),
                ThreadDetails.createThreadsVector(builder, intArrayOf()),
            )
        return Report.createReport(
            builder,
            sdk,
            ReportType.NativeCrash,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, intArrayOf(errorOffset)),
            threadDetailsOffset,
            Report.createBinaryImagesVector(builder, intArrayOf()),
            stateOffset = 0,
            featureFlagsOffset = 0,
        )
    }

    private fun createErrorOffset(
        builder: FlatBufferBuilder,
        description: String?,
        tombstone: Tombstone,
        frameOffsets: IntArray,
    ): Int {
        val signalName = tombstone.signalInfo.name
        val errorName = signalName.ifEmpty { description ?: "Native crash" }
        val reason = builder.createString(errorName)
        val causeText =
            tombstone.causesList.firstOrNull()?.humanReadable ?: tombstone.abortMessage.ifEmpty {
                signalsByNumber.values.firstOrNull { it.name == signalName }?.description ?: "Native crash"
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

    private data class SignalInfo(
        val name: String,
        val description: String,
    )
}

/**
 * Returns the imageId to use in the generated Report for a MemoryMapping. Ideally we want the BuildID,
 * but if this is not possible we'll use the mapping name (e.g. the filename) or generate a name if no other
 * data is available.
 */
fun TombstoneProtos.MemoryMapping.imageId(): String =
    buildId.ifBlank { mappingName }.ifBlank {
        anonymousName()
    }

/**
 * Returns the path to use in the generated Report for a MemoryMapping. If a mapping name is not provided,
 * generate an anonymous name.
 */
fun TombstoneProtos.MemoryMapping.path(): String = mappingName.ifBlank { anonymousName() }

/**
 * An anonymous name for a MemoryMapping. This mimics how the Android tombstone parser handles these images.
 */
fun TombstoneProtos.MemoryMapping.anonymousName(): String = "<anonymous:${java.lang.Long.toHexString(beginAddress)}>"
