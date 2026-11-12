// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.task

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BDCLIDownloaderTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `downloadIfNeeded does not throw when called concurrently`() {
        val binDir = tempDir.newFolder("bin")
        val bdFile = File(binDir, "bd")

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val errorCounter = AtomicInteger(0)
        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val downloader = BDCLIDownloader(bdFile) {
                        "bd".byteInputStream()
                    }
                    downloader.downloadIfNeeded()
                } catch (e: Exception) {
                    errorCounter.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(0, errorCounter.get())
        assertTrue(bdFile.exists())
        assertTrue(bdFile.isFile)
        assertEquals(BDCLIDownloader(bdFile).bdcliVersion, File(binDir, "bd.version").readText())
        val tempFiles = binDir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue(tempFiles.isEmpty())
    }

    @Test
    fun `downloadIfNeeded replaces an older CLI version`() {
        val binDir = tempDir.newFolder("bin")
        val bdFile = File(binDir, "bd")
        bdFile.writeText("old-bd")
        File(binDir, "bd.version").writeText("0.2.23")
        val downloadCount = AtomicInteger(0)

        BDCLIDownloader(bdFile) {
            downloadCount.incrementAndGet()
            "new-bd".byteInputStream()
        }.downloadIfNeeded()

        assertEquals(1, downloadCount.get())
        assertEquals("new-bd", bdFile.readText())
        assertEquals(BDCLIDownloader(bdFile).bdcliVersion, File(binDir, "bd.version").readText())
    }

    @Test
    fun `streams large downloads without buffering the binary`() {
        val bdFile = File(tempDir.newFolder("bin"), "bd")
        val downloadSize = 192L * 1024 * 1024
        var remaining = downloadSize
        var closed = false
        val download = object : InputStream() {
            override fun read(): Int {
                if (remaining == 0L) return -1
                remaining--
                return 0
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                buffer.fill(0, offset, offset + count)
                remaining -= count
                return count
            }

            override fun close() {
                closed = true
            }
        }

        BDCLIDownloader(bdFile) { download }.downloadIfNeeded()

        assertEquals(downloadSize, bdFile.length())
        assertTrue(bdFile.canExecute())
        assertTrue(closed)
    }

    @Test
    fun `failed download closes the stream and preserves the installed CLI`() {
        val binDir = tempDir.newFolder("bin")
        val bdFile = File(binDir, "bd")
        bdFile.writeText("old-bd")
        val versionFile = File(binDir, "bd.version")
        versionFile.writeText("0.2.23")
        var firstByte = true
        var closed = false
        val download = object : InputStream() {
            override fun read(): Int {
                if (firstByte) {
                    firstByte = false
                    return 0
                }
                throw IOException("download interrupted")
            }

            override fun close() {
                closed = true
            }
        }

        val error = assertFailsWith<IOException> {
            BDCLIDownloader(bdFile) { download }.downloadIfNeeded()
        }

        assertEquals("download interrupted", error.cause?.message)
        assertTrue(closed)
        assertEquals("old-bd", bdFile.readText())
        assertEquals("0.2.23", versionFile.readText())
        assertFalse(binDir.walkTopDown().any { it.name.endsWith(".tmp") })
    }
}
