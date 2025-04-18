// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.os.Debug

private const val KB = 1024L

internal class MemoryMetricsProvider(
    private val activityManager: ActivityManager,
) : IMemoryMetricsProvider {
    // We only save the threshold on first access since it's a constant value obtained via a rather expensive Binder call
    private val memoryThresholdBytes: Long by lazy {
        ActivityManager
            .MemoryInfo()
            .also { memoryInfo ->
                activityManager.getMemoryInfo(memoryInfo)
            }.threshold
    }

    override fun getMemoryAttributes(): Map<String, String> =
        mapOf(
            "_jvm_used_kb" to usedJvmMemoryBytes().bToKb(),
            "_jvm_total_kb" to totalJvmMemoryBytes().bToKb(),
            "_native_used_kb" to allocatedNativeHeapSizeBytes().bToKb(),
            "_native_total_kb" to totalNativeHeapSizeBytes().bToKb(),
            "_threshold_mem_kb" to memoryThresholdBytes.bToKb(),
            "_is_memory_low" to isMemoryLow().toString(),
            "_memory_class" to memoryClassMB().toString(),
        )

    override fun isMemoryLow(): Boolean = usedJvmMemoryBytes() > memoryThresholdBytes

    private fun Long.bToKb(): String = (this / KB).toString()

    private fun totalJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory()

    private fun usedJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private fun allocatedNativeHeapSizeBytes(): Long = Debug.getNativeHeapAllocatedSize()

    private fun totalNativeHeapSizeBytes(): Long = Debug.getNativeHeapSize()

    private fun memoryClassMB(): Int = activityManager.memoryClass
}
