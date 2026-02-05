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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
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
                    val downloader = BDCLIDownloader(bdFile)
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
        val tempFiles = binDir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue(tempFiles.isEmpty())
    }
}