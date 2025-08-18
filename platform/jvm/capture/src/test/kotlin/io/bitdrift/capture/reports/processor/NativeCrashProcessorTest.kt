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

    fun makeReport(tombstone: TombstoneProtos.Tombstone): Report {
        val out = ByteArrayOutputStream()
        tombstone.writeTo(out)
        val tombstone = ByteArrayInputStream(out.toByteArray())

        val flatBufferBuilder = FlatBufferBuilder()
        val reportOffset = NativeCrashProcessor.process(flatBufferBuilder, 0, 0, 0, "description", tombstone)

        flatBufferBuilder.finish(reportOffset)

        return Report.getRootAsReport(flatBufferBuilder.dataBuffer())
    }
}
