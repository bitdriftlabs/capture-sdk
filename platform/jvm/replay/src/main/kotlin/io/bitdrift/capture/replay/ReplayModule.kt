// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.content.Context
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.internal.DisplayManagers
import io.bitdrift.capture.replay.internal.ReplayCapture
import io.bitdrift.capture.replay.internal.ReplayCaptureController
//import io.bitdrift.capture.replay.internal.ReplayDependencies

// TODO(murki): [Replay] Re-enable internal diagnostic logging
// This is the logger called from the replay module source code.
//internal typealias L = ReplayModuleInternalLogs

//internal object ReplayModuleInternalLogs {
//
//    fun v(message: String) {
//        ReplayModule.replayDependencies.logger.logVerboseInternal(message)
//    }
//
//    fun d(message: String) {
//        ReplayModule.replayDependencies.logger.logDebugInternal(message)
//    }
//
//    fun e(e: Throwable?, message: String) {
//        ReplayModule.replayDependencies.logger.logErrorInternal(message, e)
//    }
//}

/**
 * Sets up and controls the replay feature
 * @param errorHandler allows to report internal errors to our backend
 * @param replayLogger the callback to use to report replay captured screens
 * @param sessionReplayConfiguration the configuration to use
 * @param runtime allows for the feature to be remotely disabled
 */
class ReplayModule(
    internal val errorHandler: ErrorHandler,
    internal val logger: ReplayLogger,
    internal val sessionReplayConfiguration: SessionReplayConfiguration,
    context: Context,
    mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) {

    internal val displayManager: DisplayManagers
    private val replayCapture: ReplayCapture
    private val replayCaptureController: ReplayCaptureController

    init {
        displayManager = DisplayManagers()
        displayManager.init(context)
        replayCapture = ReplayCapture(sessionReplayConfiguration, errorHandler, displayManager)
        replayCaptureController = ReplayCaptureController(replayCapture, logger, mainThreadHandler)
    }

    /**
     * Prepares and emits a session replay screen log using a logger instance passed
     * at initialiation time.
     */
    fun captureScreen(skipReplayComposeViews: Boolean) {
        replayCaptureController.captureScreen(skipReplayComposeViews)
    }
}
