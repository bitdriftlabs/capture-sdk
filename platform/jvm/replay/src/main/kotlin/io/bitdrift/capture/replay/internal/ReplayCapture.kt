// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.replay.L
import io.bitdrift.capture.replay.ReplayLogger
import java.util.concurrent.ExecutorService
import kotlin.time.measureTimedValue

// This is the main logic for capturing a screen
internal class ReplayCapture(
    private val replayLogger: ReplayLogger,
    private val captureParser: ReplayParser = ReplayParser(),
    private val captureFilter: ReplayFilter = ReplayFilter(),
    private val captureDecorations: ReplayDecorations = ReplayDecorations(),
    private val replayEncoder: ReplayEncoder = ReplayEncoder(),
    private val clock: IClock = DefaultClock.getInstance(),
) {

    fun captureScreen(executor: ExecutorService, skipReplayComposeViews: Boolean) {
        val startTime = clock.elapsedRealtime()

        val encodedScreenMetrics = EncodedScreenMetrics()
        val timedValue = measureTimedValue {
            captureParser.parse(encodedScreenMetrics, skipReplayComposeViews)
        }

        executor.execute {
            captureFilter.filter(timedValue.value)?.let { filteredCapture ->
                encodedScreenMetrics.parseDuration = timedValue.duration
                encodedScreenMetrics.viewCountAfterFilter = filteredCapture.size
                val screen = captureDecorations.addDecorations(filteredCapture)
                val encodedScreen = replayEncoder.encode(screen)
                encodedScreenMetrics.captureTimeMs = clock.elapsedRealtime() - startTime
                L.d("Screen Captured: $encodedScreenMetrics")
                replayLogger.onScreenCaptured(encodedScreen, screen, encodedScreenMetrics)
            }
        }
    }
}
