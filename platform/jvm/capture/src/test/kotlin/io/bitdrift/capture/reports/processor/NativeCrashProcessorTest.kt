// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.TombstoneProtos
import io.bitdrift.capture.TombstoneProtos.BacktraceFrame
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.BinaryImage
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
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

        val file1 = requireBinaryImage(report, 0)
        assertThat(file1.loadAddress).isEqualTo(100.toULong())
        assertThat(file1.path).isEqualTo("file1")
        assertThat(file1.id).isEqualTo("build-id")

        val threadDetails = requireNotNull(report.threadDetails)
        val thread = requireNotNull(threadDetails.threads(0))
        val frame = requireNotNull(thread.stackTrace(0))
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

        val file = requireBinaryImage(report, 0)
        assertThat(file.loadAddress).isEqualTo(400.toULong())
        assertThat(file.path).isEqualTo("mapping")

        val threadDetails = requireNotNull(report.threadDetails)
        val thread = requireNotNull(threadDetails.threads(0))
        val frame = requireNotNull(thread.stackTrace(0))
        assertThat(frame.imageId).isEqualTo("mapping")
    }

    @Test
    fun `tracks filename for frames without a resolved mmap`() {
        val tombstone = makeTombstone("file", 410, null, emptyList())

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(0)
        val threadDetails = requireNotNull(report.threadDetails)
        val thread = requireNotNull(threadDetails.threads(0))
        val frame = requireNotNull(thread.stackTrace(0))
        assertThat(frame.imageId).isEqualTo("file")
    }

    @Test
    fun `handles anonymous mmap regions`() {
        val tombstone = makeTombstone("file", 410, null, listOf(SimpleMapping("", 400, 450)))

        val report = makeReport(tombstone)

        assertThat(report.binaryImagesLength).isEqualTo(1)
        val file = requireBinaryImage(report, 0)
        assertThat(file.loadAddress).isEqualTo(400.toULong())
        assertThat(file.path).isEqualTo("<anonymous:190>")

        val threadDetails = requireNotNull(report.threadDetails)
        val thread = requireNotNull(threadDetails.threads(0))
        val frame = requireNotNull(thread.stackTrace(0))
        assertThat(frame.imageId).isEqualTo("<anonymous:190>")
    }

    @Test
    fun `real tombstone extracts correct memory map as binary image`() {
        val tombstone = buildTombstoneFromFile("tombstone.bin")
        val report = makeReport(tombstone)

        // A tombstone may have multiple memory maps matching a particular buildId, this one in particular has
        // two.
        val libCaptureMemoryMaps = tombstone.memoryMappingsList.filter { it.buildId == "4439046966278476" }
        assertThat(libCaptureMemoryMaps.count()).isEqualTo(2)
        val libCaptureBinaryImage = report.findBinaryImageById("4439046966278476")
        assertThat(libCaptureBinaryImage).isNotNull()

        // We expect the binary image in the report to be based on the first memory map in the tombstone.
        assertThat(requireNotNull(libCaptureBinaryImage).loadAddress.toLong()).isEqualTo(libCaptureMemoryMaps.first()?.beginAddress)
    }

    @Test
    fun `preserves multiple threads when native backtraces are identical`() {
        val sharedFrames =
            listOf(
                SimpleFrame("file1", "function1", 110, 10),
                SimpleFrame("file2", "function2", 210, 10),
            )
        val tombstone =
            makeTombstone(
                threads =
                    listOf(
                        SimpleThread(1, "crashing-thread", sharedFrames),
                        SimpleThread(2, "worker-thread-1", sharedFrames),
                        SimpleThread(3, "worker-thread-2", sharedFrames),
                    ),
                memoryMappings =
                    listOf(
                        SimpleMapping("file1", 100, 149, "build-id-1"),
                        SimpleMapping("file2", 200, 249, "build-id-2"),
                    ),
            )

        val report = makeReport(tombstone)

        val threadDetails = requireNotNull(report.threadDetails)
        assertThat(threadDetails.threadsLength).isEqualTo(3)

        val crashingThread = requireNotNull(threadDetails.threads(0))
        val workerThread1 = requireNotNull(threadDetails.threads(1))
        val workerThread2 = requireNotNull(threadDetails.threads(2))
        assertThat(crashingThread.name).isEqualTo("crashing-thread")
        assertThat(workerThread1.name).isEqualTo("worker-thread-1")
        assertThat(workerThread2.name).isEqualTo("worker-thread-2")
        assertThat(crashingThread.stackTraceLength).isEqualTo(sharedFrames.size)
        assertThat(workerThread1.stackTraceLength).isEqualTo(sharedFrames.size)

        val error = requireNotNull(report.errors(0))
        assertThat(error.stackTraceLength).isEqualTo(sharedFrames.size)
    }

    @Test
    fun `preserves multiple threads when native backtraces are distinct`() {
        val commonFrame = SimpleFrame("file2", "common_function", 210, 10)
        val memoryMappings =
            listOf(
                SimpleMapping("file1", 100, 149, "build-id-1"),
                SimpleMapping("file2", 200, 249, "build-id-2"),
            )
        val tombstone =
            makeTombstone(
                threads =
                    listOf(
                        SimpleThread(
                            1,
                            "crashing-thread",
                            listOf(
                                SimpleFrame("file1", "crash_function", 110, 10),
                                commonFrame,
                            ),
                        ),
                        SimpleThread(
                            2,
                            "worker-thread-1",
                            listOf(
                                SimpleFrame("file1", "worker_function_1", 120, 20),
                                commonFrame,
                            ),
                        ),
                        SimpleThread(
                            3,
                            "worker-thread-2",
                            listOf(
                                SimpleFrame("file1", "worker_function_2", 130, 30),
                                commonFrame,
                            ),
                        ),
                    ),
                memoryMappings = memoryMappings,
            )

        val report = makeReport(tombstone)

        val threadDetails = requireNotNull(report.threadDetails)
        assertThat(threadDetails.threadsLength).isEqualTo(3)

        val threads = List(threadDetails.threadsLength) { index -> requireNotNull(threadDetails.threads(index)) }
        assertThat(threads.map { it.name })
            .containsExactly("crashing-thread", "worker-thread-1", "worker-thread-2")
        assertThat(threads.map { it.stackTraceLength }).containsOnly(2)
        assertThat(threads.map { requireNotNull(it.stackTrace(0)).symbolName })
            .containsExactly("crash_function", "worker_function_1", "worker_function_2")
        assertThat(threads.map { requireNotNull(it.stackTrace(1)).symbolName }).containsOnly("common_function")

        val error = requireNotNull(report.errors(0))
        assertThat(error.stackTraceLength).isEqualTo(2)
        val errorFrame0 = requireNotNull(error.stackTrace(0))
        val errorFrame1 = requireNotNull(error.stackTrace(1))
        assertThat(errorFrame0.symbolName).isEqualTo("crash_function")
        assertThat(errorFrame1.symbolName).isEqualTo("common_function")
    }

    fun Report.findBinaryImageById(id: String): BinaryImage? {
        for (i in 0 until binaryImagesLength) {
            val image = binaryImages(i)
            if (image != null && image.id == id) {
                return image
            }
        }

        return null
    }

    private fun requireBinaryImage(
        report: Report,
        index: Int,
    ): BinaryImage = requireNotNull(report.binaryImages(index))

    private fun buildTombstoneFromFile(rawFilePath: String): TombstoneProtos.Tombstone {
        val stream =
            io.bitdrift.capture.TestResourceHelper
                .getResourceAsStream(rawFilePath)
        return TombstoneProtos.Tombstone.parseFrom(stream)
    }

    data class SimpleMapping(
        val name: String,
        val start: Long,
        val end: Long,
        val buildId: String? = null,
    )

    data class SimpleFrame(
        val fileName: String,
        val functionName: String,
        val pc: Long,
        val relPc: Long,
        val buildId: String? = null,
    )

    data class SimpleThread(
        val id: Int,
        val name: String,
        val frames: List<SimpleFrame>,
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
                                        it.relPc = 10
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

    fun makeTombstone(
        threads: List<SimpleThread>,
        memoryMappings: List<SimpleMapping>,
    ): TombstoneProtos.Tombstone {
        val tombstoneBuilder = TombstoneProtos.Tombstone.newBuilder().setTid(threads.first().id)

        threads.forEach { thread ->
            tombstoneBuilder.putThreads(thread.id, buildThread(thread))
        }

        memoryMappings.forEach { mapping ->
            tombstoneBuilder.addMemoryMappings(buildMemoryMapping(mapping))
        }

        return tombstoneBuilder.build()
    }

    private fun buildThread(thread: SimpleThread): TombstoneProtos.Thread {
        val threadBuilder =
            TombstoneProtos.Thread
                .newBuilder()
                .setId(thread.id)
                .setName(thread.name)

        thread.frames.forEach { frame ->
            threadBuilder.addCurrentBacktrace(buildBacktraceFrame(frame))
        }

        return threadBuilder.build()
    }

    private fun buildBacktraceFrame(frame: SimpleFrame): BacktraceFrame {
        val frameBuilder =
            BacktraceFrame
                .newBuilder()
                .setFileName(frame.fileName)
                .setFunctionName(frame.functionName)
                .setPc(frame.pc)
                .setRelPc(frame.relPc)

        frame.buildId?.let(frameBuilder::setBuildId)

        return frameBuilder.build()
    }

    private fun buildMemoryMapping(mapping: SimpleMapping): TombstoneProtos.MemoryMapping {
        val mappingBuilder =
            TombstoneProtos.MemoryMapping
                .newBuilder()
                .setMappingName(mapping.name)
                .setBeginAddress(mapping.start)
                .setEndAddress(mapping.end)

        mapping.buildId?.let(mappingBuilder::setBuildId)

        return mappingBuilder.build()
    }

    fun makeReport(tombstone: TombstoneProtos.Tombstone): Report {
        val flatBufferBuilder = FlatBufferBuilder()
        val reportOffset =
            NativeCrashProcessor.process(
                flatBufferBuilder,
                0,
                0,
                0,
                "description",
                tombstone.toInputStream(),
                terminatingSignalNumber = 0,
            )

        flatBufferBuilder.finish(reportOffset)

        return Report.getRootAsReport(flatBufferBuilder.dataBuffer())
    }

    private fun TombstoneProtos.Tombstone.toInputStream(): ByteArrayInputStream {
        val tombstoneBytes = ByteArrayOutputStream()
        writeTo(tombstoneBytes)
        return ByteArrayInputStream(tombstoneBytes.toByteArray())
    }
}
