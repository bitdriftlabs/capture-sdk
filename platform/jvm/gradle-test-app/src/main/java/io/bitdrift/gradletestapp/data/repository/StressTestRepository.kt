// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.os.Handler
import android.os.Looper
import io.bitdrift.capture.Capture
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class StressTestRepository {
    private val oomList = mutableListOf<ByteArray>()

    fun increaseMemoryPressure(targetPercent: Int) {
        if (oomList.isNotEmpty()) {
            return
        }
        val targetUsage = (maxMemory() * targetPercent / 100.0).toLong()
        Thread {
            while (true) {
                oomList.add(ByteArray(1024 * 1024))
                Thread.sleep(250)
                if (usedMemory() >= targetUsage) {
                    Capture.Logger.logError {
                        "Increased memory pressure to $targetPercent%"
                    }
                    break
                }
            }
        }.start()
    }

    fun triggerJankyFrames(durationMs: Long) {
        Handler(Looper.getMainLooper()).post {
            Thread.sleep(durationMs)
            Timber.d("Jank sleep done: ${durationMs}ms")
        }
    }

    fun triggerStrictModeViolation() {
        Handler(Looper.getMainLooper()).post {
            triggerDiskReadViolation()
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

    private fun maxMemory(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    private fun usedMemory(): Long {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }
}

