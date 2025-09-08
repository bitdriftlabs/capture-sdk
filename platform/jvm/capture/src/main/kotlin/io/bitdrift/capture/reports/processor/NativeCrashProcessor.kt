// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos.Tombstone
import io.bitdrift.capture.reports.binformat.v1.BinaryImage
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
    private val obfuscatedJvmPatterns =
        listOf(
            // e.g. "a.b"
            Regex("^[a-z][a-z0-9]*\\.[a-z][a-z0-9]*$"),
            // e.g. "cn2.a.a", "a1.b2.c3"
            Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){2,}$"),
            // e.g. "a$b", "cn2$a$b"
            Regex("^[a-z][a-z0-9]*(\\$[a-z][a-z0-9]*)+$"),
        )

    private val jvmExtensions = listOf(".dex", ".jar", ".oat", ".odex")

    private val jvmLibraries =
        listOf(
            "libart.so",
            "libjvm.so",
            "libopenjdk.so",
            "libopenjdkjvm.so",
            "core-libart.jar",
            "core-oj.jar",
        )
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

        val referencedBuildIds = mutableSetOf<String>()

        tombstone.threadsMap.forEach { (tid, thread) ->
            val frameOffsets =
                thread.currentBacktraceList
                    .map { frame ->
                        val frameAddress = frame.pc.toULong()
                        val isNativeFrame = frame.pc != 0L
                        val imageId: String? = if (isNativeFrame) frame.buildId else null

                        if (!frame.buildId.isNullOrEmpty()) {
                            referencedBuildIds.add(frame.buildId)
                        }

                        val mappingName =
                            tombstone.memoryMappingsList
                                .find { it.buildId == frame.buildId }
                                ?.mappingName
                        val isJvmFrame = isJvmFrame(frame.functionName, mappingName)
                        val frameType =
                            when {
                                isJvmFrame -> FrameType.JVM
                                isNativeFrame -> FrameType.AndroidNative
                                else -> FrameType.AndroidNative
                            }

                        val frameData =
                            FrameData(
                                symbolName = frame.functionName,
                                fileName = if (!isNativeFrame) frame.fileName else null,
                                imageId = imageId,
                                frameAddress = frameAddress,
                            )
                        ReportFrameBuilder.build(
                            frameType,
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

        val allReferencedMappings =
            tombstone.memoryMappingsList
                .filter { mapping ->
                    mapping.buildId.isNotEmpty() ||
                        referencedBuildIds.contains(mapping.buildId)
                }.groupBy { it.buildId }
                .mapValues { (_, mappings) ->
                    mappings.minByOrNull { it.beginAddress }
                }

        allReferencedMappings.forEach { (buildId, mapping) ->
            mapping?.let {
                binaryImageOffsets.add(
                    BinaryImage.createBinaryImage(
                        builder,
                        builder.createString(buildId.ifEmpty { "unknown" }),
                        builder.createString(it.mappingName),
                        it.beginAddress.toULong(),
                    ),
                )
            }
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
        val reason =
            builder.createString(signalName.ifEmpty { description })
        val causeText =
            tombstone.causesList.firstOrNull()?.humanReadable
                ?: tombstone.abortMessage.ifEmpty {
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

    /**
     * Detects if a frame represents a JVM/Java frame based on function name patterns
     * and image file extensions.
     */
    private fun isJvmFrame(
        functionName: String?,
        mappingName: String?,
    ): Boolean {
        if (functionName.isNullOrEmpty()) return false

        if (obfuscatedJvmPatterns.any { it.matches(functionName) }) {
            return true
        }

        mappingName?.let { name ->
            val lowerName = name.lowercase()

            if (jvmExtensions.any { lowerName.endsWith(it) } ||
                jvmLibraries.any { lowerName.endsWith(it) }
            ) {
                return true
            }
        }

        return false
    }
}
