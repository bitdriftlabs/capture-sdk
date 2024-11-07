// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.SessionReplayConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

// Captures wireframe and pixel perfect representations of app's screen.
internal class ReplayCaptureController(
    private val replayCapture: ReplayCapture,
    private val logger: ReplayLogger,
    private val mainThreadHandler: MainThreadHandler,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "io.bitdrift.capture.session-replay")
    },

) {
    fun captureScreen(skipReplayComposeViews: Boolean) {
        mainThreadHandler.run {
            replayCapture.captureScreen(executor, skipReplayComposeViews) { byteArray, screen, metrics ->
                logger.onScreenCaptured(byteArray, screen, metrics)
            }
        }
    }
}
