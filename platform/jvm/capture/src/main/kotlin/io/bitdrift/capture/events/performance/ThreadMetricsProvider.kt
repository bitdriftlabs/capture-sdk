// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

/**
 * Concrete implementation of [IMemoryMetricsProvider]
 */
internal class ThreadMetricsProvider : IThreadMetricsProvider {
    override fun getThreadAttributes(): Map<String, String> = mapOf(THREAD_COUNT_KEY to getActiveThreadCount())

    private fun getActiveThreadCount(): String = Thread.activeCount().toString()

    private companion object {
        private const val THREAD_COUNT_KEY = "_total_active_thread_count"
    }
}
