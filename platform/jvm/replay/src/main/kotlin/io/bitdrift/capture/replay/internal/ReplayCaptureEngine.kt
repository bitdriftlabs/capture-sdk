// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.IReplayLogger
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import java.util.concurrent.ExecutorService
import kotlin.time.measureTimedValue

// This is the main logic for capturing a screen
internal class ReplayCaptureEngine(
    sessionReplayConfiguration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    private val logger: IReplayLogger,
    private val mainThreadHandler: MainThreadHandler,
    windowManager: IWindowManager,
    displayManager: DisplayManagers,
    private val executor: ExecutorService,
    private val captureParser: ReplayParser =
        ReplayParser(
            sessionReplayConfiguration,
            errorHandler,
            windowManager,
            displayManager,
        ),
    private val replayEncoder: ReplayEncoder = ReplayEncoder(),
    private val clock: IClock = DefaultClock.getInstance(),
) {
    private var previousCapture: List<ReplayRect>? = null

    fun captureScreen(skipReplayComposeViews: Boolean) {
        mainThreadHandler.run {
            captureScreen(skipReplayComposeViews) { byteArray, screen, metrics ->
                logger.onScreenCaptured(byteArray, screen, metrics)
            }
        }
    }

    private fun captureScreen(
        skipReplayComposeViews: Boolean,
        completion: (encodedScreen: ByteArray, screen: List<ReplayRect>, metrics: ReplayCaptureMetrics) -> Unit,
    ) {
        val startTime = clock.elapsedRealtime()

        val replayCaptureMetrics = ReplayCaptureMetrics()
        val timedValue =
            measureTimedValue {
                captureParser.parse(replayCaptureMetrics, skipReplayComposeViews)
            }

        executor.execute {
            filter(timedValue.value)?.let { filteredCapture ->
                replayCaptureMetrics.parseDuration = timedValue.duration
                replayCaptureMetrics.viewCountAfterFilter = filteredCapture.size
                val encodedScreen = replayEncoder.encode(filteredCapture)
                replayCaptureMetrics.encodingTimeMs =
                    clock.elapsedRealtime() - startTime - replayCaptureMetrics.parseDuration.inWholeMilliseconds
                SessionReplayController.L.d("Screen Captured: $replayCaptureMetrics")
                completion(encodedScreen, filteredCapture, replayCaptureMetrics)
            }
        }
    }

    private fun filter(capture: List<ReplayRect>): List<ReplayRect>? {
        // This capture is identical to the previous one, filter it out
        return if (capture == previousCapture) {
            null
        } else {
            previousCapture = capture
            capture
        }
    }
}
