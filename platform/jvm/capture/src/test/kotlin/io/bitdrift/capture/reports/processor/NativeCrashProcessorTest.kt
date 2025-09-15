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
                                        it.fileName = "file1"
                                        it.buildId = "build-id"
                                        it.pc = 110
                                    },
                            ).build(),
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file1"
                                it.buildId = "build-id"
                                it.beginAddress = 100
                                it.endAddress = 149
                            },
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file1"
                                it.buildId = "build-id"
                                it.beginAddress = 150
                                it.beginAddress = 199
                            },
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file1"
                                it.buildId = "build-id"
                                it.beginAddress = 200
                                it.endAddress = 250
                            },
                    )
                }.build()

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)

        val file1 = report.binaryImages(0)!!
        assertThat(file1.loadAddress).isEqualTo(100.toULong())
        assertThat(file1.path).isEqualTo("file1")
        assertThat(file1.id).isEqualTo("build-id")
    }

    @Test
    fun `handles missing buildId in binary images`() {
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
                                        it.fileName = "file"
                                        it.pc = 410
                                    },
                            ).build(),
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file"
                                it.beginAddress = 400
                                it.endAddress = 449
                            },
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file"
                                it.beginAddress = 450
                                it.endAddress = 499
                            },
                    )
                    it.addMemoryMappings(
                        TombstoneProtos.MemoryMapping
                            .newBuilder()
                            .also {
                                it.mappingName = "file"
                                it.beginAddress = 500
                                it.endAddress = 505
                            },
                    )
                }.build()

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)

        val file = report.binaryImages(0)!!
        assertThat(file.loadAddress).isEqualTo(400.toULong())
        assertThat(file.path).isEqualTo("file")
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
