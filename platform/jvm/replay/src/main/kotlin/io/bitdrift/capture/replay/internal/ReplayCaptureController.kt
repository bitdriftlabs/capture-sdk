// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.SessionReplayConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// Controls the triggering of screen captures at regular time interval
internal class ReplayCaptureController(
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "io.bitdrift.capture.session-replay")
    },
    private val sessionReplayConfiguration: SessionReplayConfiguration = ReplayModule.replayDependencies.sessionReplayConfiguration,
    private val replayCapture: ReplayCapture = ReplayModule.replayDependencies.replayCapture,
    private val runtime: Runtime,
) {

    private var executionHandle: ScheduledFuture<*>? = null

    fun start() {
        executionHandle = executor.scheduleWithFixedDelay(
            { captureScreen() },
            0L, // initialDelay
            sessionReplayConfiguration.captureIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun stop() {
        executionHandle?.cancel(false)
    }

    private fun captureScreen() {
        if (!runtime.isEnabled(RuntimeFeature.SESSION_REPLAY)) {
            return
        }
        val skipReplayComposeViews = !runtime.isEnabled(RuntimeFeature.SESSION_REPLAY_COMPOSE)
        mainThreadHandler.run { replayCapture.captureScreen(executor, skipReplayComposeViews) }
    }
}
