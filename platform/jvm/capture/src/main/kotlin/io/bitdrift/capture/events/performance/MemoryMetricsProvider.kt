// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.os.Debug
import kotlin.math.roundToLong

private const val KB = 1024L

internal class MemoryMetricsProvider(
    private val activityManager: ActivityManager,
) : IMemoryMetricsProvider {
    var runtime: io.bitdrift.capture.common.Runtime? = null

    private val memoryInfo: ActivityManager.MemoryInfo by lazy {
        ActivityManager.MemoryInfo()
    }

    // We only save the threshold on first access since it's a constant value obtained via a rather expensive Binder call
    private val memoryThresholdBytes: Long by lazy {
        getCurrentMemoryInfo().threshold
    }

    override fun getMemoryAttributes(): Map<String, String> =
        buildMap {
            put("_jvm_used_kb", usedJvmMemoryBytes().bToKb())
            put("_jvm_total_kb", totalJvmMemoryBytes().bToKb())
            put("_jvm_max_kb", maxJvmMemoryBytes().bToKb())
            put("_jvm_utilization_percent", jvmUtilizationPercent())
            put("_native_used_kb", allocatedNativeHeapSizeBytes().bToKb())
            put("_native_total_kb", totalNativeHeapSizeBytes().bToKb())
            put("_memory_class", memoryClassMB().toString())
            put("_threshold_mem_kb", memoryThresholdBytes.bToKb())
            put("_available_device_memory", availableDeviceMemory().bToKb())
            put("_is_memory_low", if (isMemoryLow()) "1" else "0")
        }

    override fun getMemoryClass(): Map<String, String> = buildMap { put("_memory_class", memoryClassMB().toString()) }

    override fun isMemoryLow(): Boolean = availableDeviceMemory() <= memoryThresholdBytes

    private fun Long.bToKb(): String = (this / KB).toString()

    private fun totalJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory()

    private fun usedJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private fun usedJvmMemoryBytes(rt: Runtime): Long = rt.totalMemory() - rt.freeMemory()

    private fun maxJvmMemoryBytes(): Long = Runtime.getRuntime().maxMemory()

    private fun allocatedNativeHeapSizeBytes(): Long = Debug.getNativeHeapAllocatedSize()

    private fun totalNativeHeapSizeBytes(): Long = Debug.getNativeHeapSize()

    private fun memoryClassMB(): Int = activityManager.memoryClass

    private fun availableDeviceMemory(): Long = getCurrentMemoryInfo().availMem

    private fun getCurrentMemoryInfo(): ActivityManager.MemoryInfo {
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    private fun jvmUtilizationPercent(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()

        if (maxMemory == 0L) return "0"

        val usedMemory = usedJvmMemoryBytes(runtime).coerceIn(0L, maxMemory)
        val percentUsed = (usedMemory.toDouble() / maxMemory * 100).coerceIn(0.0, 100.0)

        return percentUsed.roundToLong().toString()
    }
}
