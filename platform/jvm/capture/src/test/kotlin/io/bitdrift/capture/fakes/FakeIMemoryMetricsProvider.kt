// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import io.bitdrift.capture.events.performance.IMemoryMetricsProvider

/**
 * Fake [IMemoryMetricsProvider] with default memory attribute values
 */
class FakeIMemoryMetricsProvider : IMemoryMetricsProvider {
    private var exception: Exception? = null

    override fun getMemoryAttributes(): Map<String, String> {
        exception?.let {
            throw it
        }
        return DEFAULT_MEMORY_ATTRIBUTES_MAP
    }

    fun clear() {
        exception = null
    }

    fun setException(exception: Exception) {
        this.exception = exception
    }

    companion object {
        val DEFAULT_MEMORY_ATTRIBUTES_MAP =
            mapOf(
                "_jvm_used_mb" to "0.1",
                "_jvm_total_mb" to "0.5",
                "_native_used_mb" to "0.2",
                "_native_total_mb" to "0.5",
                "_memory_class" to "1",
            )
    }
}
