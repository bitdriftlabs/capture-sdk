// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import androidx.collection.MutableScatterMap
import kotlin.time.Duration

/**
 * Metrics about a screen capture
 * @param viewCount The number of views in the capture
 * @param composeViewCount The number of Compose views in the capture
 * @param errorViewCount The number of views that caused an error during capture
 * @param exceptionCausingViewCount The number of views that caused an exception during capture
 * @param viewCountAfterFilter The number of views after filtering
 * @param parseDuration The time it took to parse the view tree
 * @param encodingTimeMs The time it took to encode all replay elements
 */
data class ReplayCaptureMetrics(
    var viewCount: Int = 0,
    var composeViewCount: Int = 0,
    var errorViewCount: Int = 0,
    var exceptionCausingViewCount: Int = 0,
    var viewCountAfterFilter: Int = 0,
    var parseDuration: Duration = Duration.ZERO,
    var encodingTimeMs: Long = 0L,
) {
    private val totalDurationMs: Long
        get() = parseDuration.inWholeMilliseconds + encodingTimeMs

    /**
     * Convert the metrics to a map
     */
    fun toMap(): Map<String, String> =
        MutableScatterMap<String, String>()
            .apply {
                put("view_count", viewCount.toString())
                put("compose_view_count", composeViewCount.toString())
                put("view_count_after_filter", viewCountAfterFilter.toString())
                put("error_view_count", errorViewCount.toString())
                put("exception_causing_view_count", exceptionCausingViewCount.toString())
                put("parse_duration_ms", parseDuration.inWholeMilliseconds.toString())
                put("encoding_time_ms", encodingTimeMs.toString())
                put("total_duration_ms", totalDurationMs.toString())
            }.asMap()
}
