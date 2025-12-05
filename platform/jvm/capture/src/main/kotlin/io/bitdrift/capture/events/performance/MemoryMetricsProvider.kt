// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.os.Debug
import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.providers.fieldsOf

private const val KB = 1024L

internal class MemoryMetricsProvider(
    private val activityManager: ActivityManager,
    private val jvmMemoryProvider: JvmMemoryProvider = DefaultJvmMemoryProvider(),
) : IMemoryMetricsProvider {
    var runtime: io.bitdrift.capture.common.Runtime? = null

    private val appLowMemoryConfigThreshold by lazy {
        getConfiguredLowMemoryPercentThreshold()
    }

    override fun getMemoryAttributes(): InternalFields =
        fieldsOf(
            "_jvm_used_kb" to jvmMemoryProvider.usedMemoryBytes().bToKb(),
            "_jvm_total_kb" to jvmMemoryProvider.totalMemoryBytes().bToKb(),
            "_jvm_max_kb" to jvmMemoryProvider.maxMemoryBytes().bToKb(),
            "_native_used_kb" to allocatedNativeHeapSizeBytes().bToKb(),
            "_native_total_kb" to totalNativeHeapSizeBytes().bToKb(),
            "_memory_class" to memoryClassMB().toString(),
            "_jvm_used_percent" to "%.3f".format(jvmUsedPercent()),
        )

    override fun getMemoryClass(): InternalFields = fieldsOf("_memory_class" to memoryClassMB().toString())

    override fun isMemoryLow(): Boolean {
        val thresholdPercent = appLowMemoryConfigThreshold ?: return false
        return jvmUsedPercent() >= thresholdPercent
    }

    private fun getConfiguredLowMemoryPercentThreshold(): Int? {
        val threshold =
            runtime?.getConfigValue(RuntimeConfig.APP_LOW_MEMORY_PERCENT_THRESHOLD) ?: return null
        // Guarding in case of miss configuration
        if (threshold < MIN_LOW_MEMORY_PERCENT_THRESHOLD || threshold > 100) return null
        return threshold
    }

    private fun Long.bToKb(): String = (this / KB).toString()

    private fun allocatedNativeHeapSizeBytes(): Long = Debug.getNativeHeapAllocatedSize()

    private fun totalNativeHeapSizeBytes(): Long = Debug.getNativeHeapSize()

    private fun memoryClassMB(): Int = activityManager.memoryClass

    private fun jvmUsedPercent(): Double = jvmMemoryProvider.usedMemoryBytes().toDouble() / jvmMemoryProvider.maxMemoryBytes() * 100

    private companion object {
        private const val MIN_LOW_MEMORY_PERCENT_THRESHOLD = 50
    }
}

internal interface JvmMemoryProvider {
    fun totalMemoryBytes(): Long

    fun usedMemoryBytes(): Long

    fun maxMemoryBytes(): Long
}

internal class DefaultJvmMemoryProvider : JvmMemoryProvider {
    override fun totalMemoryBytes(): Long = Runtime.getRuntime().totalMemory()

    override fun usedMemoryBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    override fun maxMemoryBytes(): Long = Runtime.getRuntime().maxMemory()
}
