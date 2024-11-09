// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.content.Context
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.replay.internal.ReplayCaptureEngine

/**
 * Sets up and controls the replay feature
 * @param errorHandler allows to report internal errors to our backend
 * @param replayLogger the callback to use to report replay captured screens
 * @param sessionReplayConfiguration the configuration to use
 * @param runtime allows for the feature to be remotely disabled
 */
class ReplayCaptureController(
    errorHandler: ErrorHandler,
    logger: ReplayLogger,
    sessionReplayConfiguration: SessionReplayConfiguration,
    context: Context,
) {
    private val replayCaptureEngine: ReplayCaptureEngine

    init {
        L.logger = logger
        replayCaptureEngine = ReplayCaptureEngine(
            sessionReplayConfiguration,
            errorHandler,
            context,
            logger,
        )
    }

    /**
     * Prepares and emits a session replay screen log using a logger instance passed
     * at initialization time.
     */
    fun captureScreen(skipReplayComposeViews: Boolean) {
        replayCaptureEngine.captureScreen(skipReplayComposeViews)
    }

    internal object L {
        internal var logger: ReplayLogger? = null

        fun v(message: String) {
            logger?.logVerboseInternal(message)
        }

        fun d(message: String) {
            logger?.logDebugInternal(message)
        }

        fun e(e: Throwable?, message: String) {
            logger?.logErrorInternal(message, e)
        }
    }
}
