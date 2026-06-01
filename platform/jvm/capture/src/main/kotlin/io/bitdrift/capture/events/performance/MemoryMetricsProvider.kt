// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.os.Debug
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldOf
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.fieldsOfOptional

private const val KB = 1024L

internal class MemoryMetricsProvider(
    private val activityManager: ActivityManager,
    private val jvmMemoryProvider: JvmMemoryProvider = DefaultJvmMemoryProvider(),
) : IMemoryMetricsProvider {
    var runtime: io.bitdrift.capture.common.Runtime? = null

    private val appWarningMemoryConfigThreshold: Int? by lazy {
        getConfiguredPercentThreshold(RuntimeConfig.APP_WARNING_MEMORY_PERCENT_THRESHOLD)
    }
    private val appCriticalMemoryConfigThreshold: Int? by lazy {
        getConfiguredPercentThreshold(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)
    }

    override fun getMemoryAttributes(): ArrayFields =
        combineFields(
            fieldsOf(
                "_jvm_used_kb" to jvmMemoryProvider.usedMemoryBytes().bToKb(),
                "_jvm_total_kb" to jvmMemoryProvider.totalMemoryBytes().bToKb(),
                "_jvm_max_kb" to jvmMemoryProvider.maxMemoryBytes().bToKb(),
                "_native_used_kb" to allocatedNativeHeapSizeBytes().bToKb(),
                "_native_total_kb" to totalNativeHeapSizeBytes().bToKb(),
                "_memory_class" to memoryClassMB().toString(),
                "_jvm_used_percent" to "%.3f".format(jvmUsedPercent()),
            ),
            fieldsOfOptional(
                "_is_memory_low" to appCriticalMemoryConfigThreshold?.let { if (isMemoryLow()) "1" else "0" },
            ),
        )

    override fun getMemoryClass(): ArrayFields = fieldOf("_memory_class", memoryClassMB().toString())

    override fun isMemoryLow(): Boolean {
        val thresholdPercent = appCriticalMemoryConfigThreshold ?: return false
        return jvmUsedPercent() >= thresholdPercent
    }

    override fun getCurrentJvmMemoryPressureLevel(): MemoryPressureLevel {
        val currentPercent = jvmUsedPercent()
        val warningThreshold = appWarningMemoryConfigThreshold
        val criticalThreshold = appCriticalMemoryConfigThreshold

        return if (warningThreshold == null || criticalThreshold == null) {
            MemoryPressureLevel.Unknown
        } else if (currentPercent < warningThreshold) {
            MemoryPressureLevel.Normal
        } else if (currentPercent < criticalThreshold) {
            MemoryPressureLevel.Warning
        } else {
            MemoryPressureLevel.Critical
        }
    }

    private fun getConfiguredPercentThreshold(configThreshold: RuntimeConfig): Int? {
        val threshold =
            runtime?.getConfigValue(configThreshold) ?: return null
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

/**
 * The current MemoryPressure Level
 */
enum class MemoryPressureLevel(
    /**
     * The equivalent fbs model
     */
    val nativeValue: Int,
) {
    /**
     * Memory pressure level is unknown.
     */
    Unknown(0),

    /**
     * Memory usage is below the warning threshold.
     */
    Normal(1),

    /**
     * Memory usage is at or above the warning threshold but still below the critical threshold.
     */
    Warning(2),

    /**
     * Memory usage is at or above the critical threshold.
     */
    Critical(3),
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
