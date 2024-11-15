// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.replay.ScreenshotCaptureMetrics

internal class ScreenshotMetricsStopwatch(
    private val clock: IClock = DefaultClock.getInstance(),
) {
    private var startMs: Long = 0
    private var screenshotTimeMs: Long = 0
    private var screenshotAllocationByteCount: Int = 0
    private var screenshotByteCount: Int = 0
    private var compressionTimeMs: Long = 0
    private var compressionByteCount: Int = 0

    fun start() {
        startMs = clock.elapsedRealtime()
        screenshotTimeMs = 0
        screenshotAllocationByteCount = 0
        screenshotByteCount = 0
        compressionTimeMs = 0
        compressionByteCount = 0
    }

    fun screenshot(screenshotAllocationByteCount: Int, screenshotByteCount: Int) {
        screenshotTimeMs = clock.elapsedRealtime() - startMs
        this.screenshotAllocationByteCount = screenshotAllocationByteCount
        this.screenshotByteCount = screenshotByteCount
    }

    fun compression(compressionByteCount: Int) {
        compressionTimeMs = clock.elapsedRealtime() - startMs - screenshotTimeMs
        this.compressionByteCount = compressionByteCount
    }

    fun data(): ScreenshotCaptureMetrics {
        return ScreenshotCaptureMetrics(
            screenshotTimeMs,
            screenshotAllocationByteCount,
            screenshotByteCount,
            compressionTimeMs,
            compressionByteCount,
        )
    }
}
