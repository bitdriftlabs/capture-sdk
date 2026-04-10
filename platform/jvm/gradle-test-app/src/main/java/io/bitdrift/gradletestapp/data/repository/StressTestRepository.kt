// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import io.bitdrift.capture.Capture
import io.bitdrift.gradletestapp.data.model.StrictModeViolationType
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class StressTestRepository(
    private val context: Context,
) {
    private val oomList = mutableListOf<ByteArray>()
    private var memoryPressureThread: Thread? = null

    fun increaseMemoryPressure(targetPercent: Int) {
        Capture.Logger.logWarning {
            "Started memory pressure trigger with $targetPercent% target"
        }
        stopAllThreads()
        oomList.clear()

        val targetUsage = (maxMemory() * targetPercent / 100.0).toLong()
        memoryPressureThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    oomList.add(ByteArray(1024 * 1024))
                    Thread.sleep(250)
                    if (usedMemory() >= targetUsage) {
                        Capture.Logger.logError {
                            "Increased memory pressure to $targetPercent%"
                        }
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.also { it.start() }
    }

    fun triggerMemoryPressureAnr() {
        stopAllThreads()
        oomList.clear()

        Thread {
            val targetUsage = (maxMemory() * 0.98).toLong()
            while (usedMemory() < targetUsage) {
                try {
                    oomList.add(ByteArray(1024 * 1024))
                } catch (e: OutOfMemoryError) {
                    break
                }
            }

            Capture.Logger.logWarning { "Memory filled to 98%, starting main thread allocations" }
            Handler(Looper.getMainLooper()).post {
                val startTime = System.currentTimeMillis()
                val tempList = mutableListOf<ByteArray>()

                while (System.currentTimeMillis() - startTime < 10000) {
                    try {
                        tempList.add(ByteArray(512 * 1024))
                        if (tempList.size > 20) {
                            tempList.clear()
                        }
                    } catch (_: OutOfMemoryError) {
                        tempList.clear()
                    }
                }
                Capture.Logger.logError { "ANR test completed after ${System.currentTimeMillis() - startTime}ms" }
            }
        }.start()
    }

    private fun stopAllThreads() {
        memoryPressureThread?.interrupt()
        memoryPressureThread = null
    }

    fun triggerJankyFrames(durationMs: Long) {
        Handler(Looper.getMainLooper()).post {
            Thread.sleep(durationMs)
            Timber.d("Jank sleep done: ${durationMs}ms")
        }
    }

    fun triggerStrictModeViolation(type: StrictModeViolationType) {
        Handler(Looper.getMainLooper()).post {
            when (type) {
                StrictModeViolationType.DiskRead -> triggerDiskReadViolation()
                StrictModeViolationType.DiskWrite -> triggerDiskWriteViolation()
                StrictModeViolationType.Network -> triggerNetworkViolation()
                StrictModeViolationType.CustomSlowCall -> triggerCustomSlowCallViolation()
                StrictModeViolationType.UntaggedSocket -> triggerUntaggedSocketViolation()
            }
        }
    }

    private fun triggerDiskReadViolation() {
        try {
            val file = File("/proc/version")
            FileInputStream(file).use { it.read() }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun triggerDiskWriteViolation() {
        try {
            File(context.cacheDir, "strictmode-write.txt").outputStream().use {
                it.write("strict-mode-write".toByteArray())
            }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun triggerNetworkViolation() {
        try {
            URL("https://10.0.2.2:1").openConnection().getInputStream().use { it.read() }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun triggerCustomSlowCallViolation() {
        StrictMode.noteSlowCall("Triggered custom StrictMode slow call")
    }

    private fun triggerUntaggedSocketViolation() {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("10.0.2.2", 1), 250)
            }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun maxMemory(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    private fun usedMemory(): Long {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }
}
