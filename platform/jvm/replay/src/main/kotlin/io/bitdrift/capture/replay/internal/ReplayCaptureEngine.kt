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
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.ReplayCaptureController
import io.bitdrift.capture.replay.IReplayLogger
import io.bitdrift.capture.replay.SessionReplayConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.time.measureTimedValue

// This is the main logic for capturing a screen
internal class ReplayCaptureEngine(
    sessionReplayConfiguration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    private val logger: IReplayLogger,
    private val mainThreadHandler: MainThreadHandler,
    windowManager: WindowManager,
    displayManager: DisplayManagers,
    private val captureParser: ReplayParser = ReplayParser(sessionReplayConfiguration, errorHandler, windowManager),
    private val captureFilter: ReplayFilter = ReplayFilter(),
    private val captureDecorations: ReplayDecorations = ReplayDecorations(displayManager, windowManager),
    private val replayEncoder: ReplayEncoder = ReplayEncoder(),
    private val clock: IClock = DefaultClock.getInstance(),
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "io.bitdrift.capture.session-replay")
    },
) {

    fun captureScreen(skipReplayComposeViews: Boolean) {
        mainThreadHandler.run {
            captureScreen(skipReplayComposeViews) { byteArray, screen, metrics ->
                logger.onScreenCaptured(byteArray, screen, metrics)
            }
        }
    }

    private fun captureScreen(
        skipReplayComposeViews: Boolean,
        completion: (encodedScreen: ByteArray, screen: FilteredCapture, metrics: EncodedScreenMetrics) -> Unit,
    ) {
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
                ReplayCaptureController.L.d("Screen Captured: $encodedScreenMetrics")
                completion(encodedScreen, screen, encodedScreenMetrics)
            }
        }
    }
}
