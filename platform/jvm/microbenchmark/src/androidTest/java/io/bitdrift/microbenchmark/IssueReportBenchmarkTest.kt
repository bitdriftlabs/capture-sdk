// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos
import io.bitdrift.capture.TombstoneProtos.BacktraceFrame
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.processor.JvmProcessor
import io.bitdrift.capture.reports.processor.NativeCrashProcessor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class IssueReportBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun processJvmCrash() {
        benchmarkRule.measureRepeated {
            buildJvmReport()
        }
    }

    @Test
    fun processNativeCrash() {
        benchmarkRule.measureRepeated {
            buildNativeReport()
        }
    }

    private fun buildJvmReport(): Int {
        val callerThread = Thread("crashing-thread")
        val allThreads = buildThreadStacks(callerThread)
        val throwable = RuntimeException("benchmark-crash")
        val builder = FlatBufferBuilder()
        val reportOffset =
            JvmProcessor.getJvmReport(
                builder = builder,
                sdk = 0,
                appMetrics = 0,
                deviceMetrics = 0,
                throwable = throwable,
                callerThread = callerThread,
                allThreads = allThreads,
                reportType = ReportType.JVMCrash,
                isFileSizeOptimizationEnabled = true,
            )
        builder.finish(reportOffset)
        return builder.sizedByteArray().size
    }

    private fun buildNativeReport(): Int {
        val tombstone = buildNativeTombstone()
        val builder = FlatBufferBuilder()
        val reportOffset =
            NativeCrashProcessor.process(
                builder = builder,
                sdk = 0,
                appMetrics = 0,
                deviceMetrics = 0,
                description = "benchmark-native-crash",
                traceInputStream = tombstone.toInputStream(),
                terminatingSignalNumber = 0,
                isFileSizeOptimizationEnabled = true,
            )
        builder.finish(reportOffset)
        return builder.sizedByteArray().size
    }

    private fun buildThreadStacks(
        callerThread: Thread,
    ): Map<Thread, Array<StackTraceElement>> =
        buildMap {
            val sharedTerminalFrames =
                arrayOf(
                    StackTraceElement("java.lang.Thread", "run", "Thread.java", 840),
                )

            repeat(DEFAULT_THREAD_COUNT) { index ->
                put(
                    Thread("worker-$index"),
                    arrayOf(
                        StackTraceElement("io.test.Worker", "run", "Worker.kt", 88),
                        *sharedTerminalFrames,
                    ),
                )
            }
            put(
                callerThread,
                arrayOf(
                    StackTraceElement("io.test.CrashingClass", "crash", "CrashingClass.kt", 99),
                    *sharedTerminalFrames,
                ),
            )
        }

    private fun buildNativeTombstone(): TombstoneProtos.Tombstone {
        val sharedFrames =
            listOf(
                SimpleNativeFrame("file1", "function1", 110, 10),
                SimpleNativeFrame("file2", "function2", 210, 10),
            )

        val tombstoneBuilder = TombstoneProtos.Tombstone.newBuilder().setTid(1)

        repeat(DEFAULT_THREAD_COUNT) { index ->
            val threadId = index + 1
            val threadBuilder =
                TombstoneProtos.Thread
                    .newBuilder()
                    .setId(threadId)
                    .setName("native-thread-$threadId")

            sharedFrames.forEach { frame ->
                threadBuilder.addCurrentBacktrace(
                    BacktraceFrame
                        .newBuilder()
                        .setFileName(frame.fileName)
                        .setFunctionName(frame.functionName)
                        .setPc(frame.pc)
                        .setRelPc(frame.relPc)
                        .build(),
                )
            }

            tombstoneBuilder.putThreads(threadId, threadBuilder.build())
        }

        tombstoneBuilder.addMemoryMappings(
            TombstoneProtos.MemoryMapping
                .newBuilder()
                .setMappingName("file1")
                .setBeginAddress(100)
                .setEndAddress(149)
                .setBuildId("build-id-1")
                .build(),
        )
        tombstoneBuilder.addMemoryMappings(
            TombstoneProtos.MemoryMapping
                .newBuilder()
                .setMappingName("file2")
                .setBeginAddress(200)
                .setEndAddress(249)
                .setBuildId("build-id-2")
                .build(),
        )

        return tombstoneBuilder.build()
    }

    private fun TombstoneProtos.Tombstone.toInputStream(): ByteArrayInputStream {
        val tombstoneBytes = ByteArrayOutputStream()
        writeTo(tombstoneBytes)
        return ByteArrayInputStream(tombstoneBytes.toByteArray())
    }

    private data class SimpleNativeFrame(
        val fileName: String,
        val functionName: String,
        val pc: Long,
        val relPc: Long,
    )

    private companion object {
        private const val DEFAULT_THREAD_COUNT = 50
    }
}
