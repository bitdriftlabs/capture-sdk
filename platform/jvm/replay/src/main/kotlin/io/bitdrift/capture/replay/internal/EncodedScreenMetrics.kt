// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import kotlin.time.Duration

/**
 * Metrics about a screen capture
 * @param viewCount The number of views in the capture
 * @param composeViewCount The number of Compose views in the capture
 * @param errorViewCount The number of views that caused an error during capture
 * @param exceptionCausingViewCount The number of views that caused an exception during capture
 * @param viewCountAfterFilter The number of views after filtering
 * @param parseDuration The time it took to parse the view tree
 * @param captureTimeMs The total time it took to capture the screen (parse + encoding)
 */
data class EncodedScreenMetrics(
    var viewCount: Int = 0,
    var composeViewCount: Int = 0,
    var errorViewCount: Int = 0,
    var exceptionCausingViewCount: Int = 0,
    var viewCountAfterFilter: Int = 0,
    var parseDuration: Duration = Duration.ZERO,
    var captureTimeMs: Long = 0L,
) {

    /**
     * Convert the metrics to a map
     */
    fun toMap(): Map<String, String> {
        /**
         * 'parseTime' is not included in the output map as it's passed to the Rust layer separately.
         */
        return mapOf(
            "viewCount" to viewCount.toString(),
            "composeViewCount" to composeViewCount.toString(),
            "viewCountAfterFilter" to viewCountAfterFilter.toString(),
            "errorViewCount" to errorViewCount.toString(),
            "exceptionCausingViewCount" to exceptionCausingViewCount.toString(),
            "captureTimeMs" to captureTimeMs.toString(),
        )
    }
}
