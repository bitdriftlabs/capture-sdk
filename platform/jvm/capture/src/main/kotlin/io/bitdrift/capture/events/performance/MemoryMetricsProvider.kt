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

private const val MB = 1024L * 1024L

internal class MemoryMetricsProvider(
    context: Context,
) : IMemoryMetricsProvider {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override fun getMemoryAttributes(): Map<String, String> =
        mapOf(
            "_jvm_used_mb" to usedJvmMemory(),
            "_jvm_total_mb" to totalJvmMemory(),
            "_native_used_mb" to allocatedNativeHeapSize(),
            "_native_total_mb" to totalNativeHeapSize(),
            "_memory_class" to memoryClass(),
        )

    private fun totalJvmMemory(): String = (Runtime.getRuntime().totalMemory() / MB).toString()

    private fun usedJvmMemory(): String = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB).toString()

    private fun allocatedNativeHeapSize(): String = (Debug.getNativeHeapAllocatedSize() / MB).toString()

    private fun totalNativeHeapSize(): String = (Debug.getNativeHeapSize() / MB).toString()

    private fun memoryClass(): String = (activityManager.memoryClass).toString()
}
