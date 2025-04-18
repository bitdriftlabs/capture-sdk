// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

/**
 * Provides Memory related attributes such as Memory class, Total JVM memory, used JVM memory, etc
 */
fun interface IMemoryMetricsProvider {
    /**
     * Reports current memory attributes
     */
    fun getMemorySnapshot(): MemorySnapshot
}

/**
 * Represents a snapshot of the device's memory state.
 *
 * @property attributes A map of key-value pairs representing various memory-related attributes.
 *                     These attributes are already formatted in the "bitdrift-standard" with values that
 *                     might include things like "_memory_class", "_is_memory_low", etc.
 * @property isMemoryLow A boolean flag indicating whether the device is currently experiencing
 *                       a low memory condition.
 */
data class MemorySnapshot(
    val attributes: Map<String, String>,
    val isMemoryLow: Boolean,
)
