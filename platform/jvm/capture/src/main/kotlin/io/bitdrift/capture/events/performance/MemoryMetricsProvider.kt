// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.os.Debug
import io.bitdrift.capture.common.RuntimeFeature

private const val KB = 1024L

internal class MemoryMetricsProvider(
    private val activityManager: ActivityManager,
) : IMemoryMetricsProvider {
    var runtime: io.bitdrift.capture.common.Runtime? = null

    // We only save the threshold on first access since it's a constant value obtained via a rather expensive Binder call
    private val memoryThresholdBytes: Long by lazy {
        return@lazy if (runtime?.isEnabled(RuntimeFeature.APP_MEMORY_PRESSURE) != false) {
            ActivityManager
                .MemoryInfo()
                .also { memoryInfo ->
                    activityManager.getMemoryInfo(memoryInfo)
                }.threshold
        } else {
            // Use sentinel value
            Long.MAX_VALUE
        }
    }

    override fun getMemoryAttributes(): Map<String, String> =
        buildMap {
            put("_jvm_used_kb", usedJvmMemoryBytes().bToKb())
            put("_jvm_total_kb", totalJvmMemoryBytes().bToKb())
            put("_jvm_max_kb", maxJvmMemoryBytes().bToKb())
            put("_native_used_kb", allocatedNativeHeapSizeBytes().bToKb())
            put("_native_total_kb", totalNativeHeapSizeBytes().bToKb())
            put("_memory_class", memoryClassMB().toString())
            memoryThresholdBytes.takeIf { it != Long.MAX_VALUE }?.let {
                put("_threshold_mem_kb", memoryThresholdBytes.bToKb())
                put("_is_memory_low", if (isMemoryLow()) "1" else "0")
            }
        }

    override fun isMemoryLow(): Boolean = usedJvmMemoryBytes() > memoryThresholdBytes

    private fun Long.bToKb(): String = (this / KB).toString()

    private fun totalJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory()

    private fun usedJvmMemoryBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private fun maxJvmMemoryBytes(): Long = Runtime.getRuntime().maxMemory()

    private fun allocatedNativeHeapSizeBytes(): Long = Debug.getNativeHeapAllocatedSize()

    private fun totalNativeHeapSizeBytes(): Long = Debug.getNativeHeapSize()

    private fun memoryClassMB(): Int = activityManager.memoryClass
}
