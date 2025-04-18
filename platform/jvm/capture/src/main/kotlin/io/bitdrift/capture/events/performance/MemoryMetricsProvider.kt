// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

private const val KB = 1024L

internal class MemoryMetricsProvider(
    context: Context,
) : IMemoryMetricsProvider {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override fun getMemorySnapshot(): MemorySnapshot {
        // we fetch the mem snapshot only once instead of doing it for each property
        val memoryInfo = memoryInfo()
        val isLowMemory = memoryInfo.lowMemory
        return MemorySnapshot(
            mapOf(
                "_jvm_used_kb" to usedJvmMemory(),
                "_jvm_total_kb" to totalJvmMemory(),
                "_native_used_kb" to allocatedNativeHeapSize(),
                "_native_total_kb" to totalNativeHeapSize(),
                "_memory_class" to memoryClass(),
                "_is_memory_low" to isLowMemory.toString(),
                "_avail_mem_kb" to memoryInfo.availMem.bToKb(),
                "_total_mem_kb" to memoryInfo.totalMem.bToKb(),
                "_threshold_mem_kb" to memoryInfo.threshold.bToKb(),
            ),
            isLowMemory,
        )
    }

    private fun totalJvmMemory(): String = Runtime.getRuntime().totalMemory().bToKb()

    private fun usedJvmMemory(): String = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()).bToKb()

    private fun allocatedNativeHeapSize(): String = Debug.getNativeHeapAllocatedSize().bToKb()

    private fun totalNativeHeapSize(): String = Debug.getNativeHeapSize().bToKb()

    private fun memoryClass(): String = (activityManager.memoryClass).toString()

    private fun memoryInfo(): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }

    private fun Long.bToKb(): String = (this / KB).toString()
}
