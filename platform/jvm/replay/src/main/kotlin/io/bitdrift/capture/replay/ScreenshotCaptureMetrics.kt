// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import androidx.collection.MutableScatterMap

/**
 * Metrics about the screenshot and compression process
 */
data class ScreenshotCaptureMetrics(
    /**
     * The time it took to take the screenshot in milliseconds
     */
    val screenshotTimeMs: Long,
    private val screenshotAllocationByteCount: Int,
    private val screenshotByteCount: Int,
    private var compressionTimeMs: Long,
    private var compressionByteCount: Int,
) {
    private val totalDurationMs: Long
        get() = screenshotTimeMs + compressionTimeMs

    /**
     * Convert the metrics to a map
     */
    fun toMap(): Map<String, String> =
        MutableScatterMap<String, String>()
            .apply {
                put("screenshot_time_ms", screenshotTimeMs.toString())
                put("screenshot_allocation_byte_count", screenshotAllocationByteCount.toString())
                put("screenshot_byte_count", screenshotByteCount.toString())
                put("compression_time_ms", compressionTimeMs.toString())
                put("compression_byte_count", compressionByteCount.toString())
                put("total_duration_ms", totalDurationMs.toString())
            }.asMap()
}
