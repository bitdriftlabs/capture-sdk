// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

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
    fun toMap(): Map<String, String> {
        return mapOf(
            "screenshot_time_ms" to screenshotTimeMs.toString(),
            "screenshot_allocation_byte_count" to screenshotAllocationByteCount.toString(),
            "screenshot_byte_count" to screenshotByteCount.toString(),
            "compression_time_ms" to compressionTimeMs.toString(),
            "compression_byte_count" to compressionByteCount.toString(),
            "total_duration_ms" to totalDurationMs.toString(),
        )
    }
}
