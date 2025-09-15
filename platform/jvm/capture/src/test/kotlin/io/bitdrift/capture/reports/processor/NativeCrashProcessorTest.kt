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
    fun `populates buildId for binary images`() {
        val tombstone =
            makeTombstone(
                "file1",
                110,
                "build-id",
                listOf(
                    SimpleMapping("file1", 100, 149, "build-id"),
                    SimpleMapping("file1", 150, 199, "build-id"),
                    SimpleMapping("mapping", 200, 250, "build-id"),
                ),
            )

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)

        val file1 = report.binaryImages(0)!!
        assertThat(file1.loadAddress).isEqualTo(100.toULong())
        assertThat(file1.path).isEqualTo("file1")
        assertThat(file1.id).isEqualTo("build-id")

        val frame =
            report.threadDetails!!.threads(0)!!.stackTrace(0)!!
        assertThat(frame.imageId).isEqualTo("build-id")
    }

    @Test
    fun `handles missing buildId in binary images`() {
        val tombstone =
            makeTombstone(
                "file",
                410,
                null,
                listOf(
                    SimpleMapping("mapping", 400, 449),
                    SimpleMapping("mapping", 450, 499),
                    SimpleMapping("mapping", 500, 505),
                ),
            )

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)

        val file = report.binaryImages(0)!!
        assertThat(file.loadAddress).isEqualTo(400.toULong())
        assertThat(file.path).isEqualTo("mapping")

        val frame =
            report.threadDetails!!.threads(0)!!.stackTrace(0)!!
        assertThat(frame.imageId).isEqualTo("mapping")
    }

    @Test
    fun `tracks filename for frames without a resolved mmap`() {
        val tombstone = makeTombstone("file", 410, null, emptyList())

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(0)
        val frame =
            report.threadDetails!!.threads(0)!!.stackTrace(0)!!
        assertThat(frame.imageId).isEqualTo("file")
    }

    @Test
    fun `handles anonymous mmap regions`() {
        val tombstone = makeTombstone("file", 410, null, listOf(SimpleMapping("", 400, 450)))

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)
        val file = report.binaryImages(0)!!
        assertThat(file.loadAddress).isEqualTo(400.toULong())
        assertThat(file.path).isEqualTo("anonymous")

        val frame =
            report.threadDetails!!.threads(0)!!.stackTrace(0)!!
        assertThat(frame.imageId).isEqualTo("anonymous")
    }

    data class SimpleMapping(
        val name: String,
        val start: Long,
        val end: Long,
        val buildId: String? = null,
    )

    fun makeTombstone(
        frameFileName: String,
        framePc: Long,
        frameBuildId: String?,
        memoryMappings: List<SimpleMapping>,
    ): TombstoneProtos.Tombstone {
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
                                    .also {
                                        it.fileName = frameFileName
                                        it.pc = framePc
                                        if (frameBuildId != null) {
                                            it.buildId = frameBuildId
                                        }
                                    }.build(),
                            ).build(),
                    )

                    for (mapping in memoryMappings) {
                        it.addMemoryMappings(
                            TombstoneProtos.MemoryMapping
                                .newBuilder()
                                .also {
                                    it.mappingName = mapping.name
                                    it.beginAddress = mapping.start
                                    it.endAddress = mapping.end
                                    if (mapping.buildId != null) {
                                        it.buildId = mapping.buildId
                                    }
                                }.build(),
                        )
                    }
                }.build()

        return tombstone
    }

    fun makeReport(tombstone: TombstoneProtos.Tombstone): Report {
        val out = ByteArrayOutputStream()
        tombstone.writeTo(out)
        val tombstoneStream = ByteArrayInputStream(out.toByteArray())

        val flatBufferBuilder = FlatBufferBuilder()
        val reportOffset =
            NativeCrashProcessor.process(flatBufferBuilder, 0, 0, 0, "description", tombstoneStream)

        flatBufferBuilder.finish(reportOffset)

        return Report.getRootAsReport(flatBufferBuilder.dataBuffer())
    }
}
