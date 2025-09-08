// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos
import io.bitdrift.capture.reports.binformat.v1.Report
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NativeCrashProcessorTest {
    @Test
    fun `dedupes by build-id and picks the lowest address memory map for report`() {
        val tombstone =
            TombstoneProtos.Tombstone
                .newBuilder()
                .setTid(1)
                .also {
                    it.putThreads(
                        1,
                        TombstoneProtos.Thread
                            .newBuilder()
                            .setId(1)
                            .addCurrentBacktrace(
                                TombstoneProtos.BacktraceFrame
                                    .newBuilder()
                                    .setBuildId("build-id"),
                            ).build(),
                    )

                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .setBuildId("build-id")
                            .setBeginAddress(200),
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .setBuildId("build-id")
                            .setBeginAddress(150),
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .setBuildId("build-id")
                            .setBeginAddress(100),
                    )
                }.build()

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)
        assertThat(report.binaryImages(0)?.loadAddress).isEqualTo(100.toULong())
    }

    @Test
    fun `detects JVM frames based on obfuscated function names`() {
        val tombstone =
            createTombstoneWithFrame(
                functionName = "cn2.a.a",
                buildId = "jvm-build-id",
                mappingName = "/system/framework/oat/arm64/app.odex",
            )

        val frame = getFirstFrame(tombstone)
        assertThat(frame?.type).isEqualTo(JVM_FRAME_TYPE)
    }

    @Test
    fun `detects JVM frames based on JVM-related image files`() {
        val tombstone =
            createTombstoneWithFrame(
                functionName = "someMethod",
                buildId = "art-build-id",
                mappingName = "/apex/com.android.art/lib64/libart.so",
            )

        val frame = getFirstFrame(tombstone)
        assertThat(frame?.type).isEqualTo(JVM_FRAME_TYPE)
    }

    @Test
    fun `detects native frames`() {
        val tombstone =
            createTombstoneWithFrame(
                functionName = "native_function",
                buildId = "native-build-id",
                mappingName = "/system/lib64/libc.so",
            )

        val frame = getFirstFrame(tombstone)
        assertThat(frame?.type).isEqualTo(NATIVE_FRAME_TYPE)
    }

    private fun makeReport(tombstone: TombstoneProtos.Tombstone): Report {
        val out = ByteArrayOutputStream()
        tombstone.writeTo(out)
        val tombstoneStream = ByteArrayInputStream(out.toByteArray())

        val flatBufferBuilder = FlatBufferBuilder()
        val reportOffset = NativeCrashProcessor.process(flatBufferBuilder, 0, 0, 0, "description", tombstoneStream)

        flatBufferBuilder.finish(reportOffset)

        return Report.getRootAsReport(flatBufferBuilder.dataBuffer())
    }

    private fun createTombstoneWithFrame(
        functionName: String,
        buildId: String,
        mappingName: String,
    ): TombstoneProtos.Tombstone =
        TombstoneProtos.Tombstone
            .newBuilder()
            .setTid(1)
            .also {
                it.putThreads(
                    1,
                    TombstoneProtos.Thread
                        .newBuilder()
                        .setId(1)
                        .addCurrentBacktrace(
                            TombstoneProtos.BacktraceFrame
                                .newBuilder()
                                .setPc(0x1000)
                                .setFunctionName(functionName)
                                .setBuildId(buildId),
                        ).build(),
                )

                it.addMemoryMappings(
                    TombstoneProtos.MemoryMapping
                        .newBuilder()
                        .setBuildId(buildId)
                        .setMappingName(mappingName)
                        .setBeginAddress(100),
                )
            }.build()

    private fun getFirstFrame(tombstone: TombstoneProtos.Tombstone): io.bitdrift.capture.reports.binformat.v1.Frame? {
        val report = makeReport(tombstone)
        val thread = report.threadDetails?.threads(0)
        assertThat(thread).isNotNull()
        return thread?.stackTrace(0)
    }

    private companion object {
        const val JVM_FRAME_TYPE: Byte = 1 // FrameType.JVM = 1
        const val NATIVE_FRAME_TYPE: Byte = 3 // FrameType.AndroidNative = 3
    }
}
