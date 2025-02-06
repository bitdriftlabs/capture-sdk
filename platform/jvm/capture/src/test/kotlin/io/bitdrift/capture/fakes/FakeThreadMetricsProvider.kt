// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import io.bitdrift.capture.events.performance.IMemoryMetricsProvider
import io.bitdrift.capture.events.performance.IThreadMetricsProvider

/**
 * Fake [IMemoryMetricsProvider] with default memory attribute values
 */
class FakeThreadMetricsProvider : IThreadMetricsProvider {
    override fun getThreadAttributes(): Map<String, String> = DEFAULT_THREAD_ATTRIBUTES_MAP

    companion object {
        val DEFAULT_THREAD_ATTRIBUTES_MAP = mapOf("_total_thread_count" to "25")
    }
}
