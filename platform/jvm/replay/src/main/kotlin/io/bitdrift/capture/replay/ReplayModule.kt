// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.content.Context
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.replay.internal.ReplayCaptureController
import io.bitdrift.capture.replay.internal.ReplayDependencies

// This is the logger called from the replay module source code.
internal typealias L = ReplayModuleInternalLogs

internal object ReplayModuleInternalLogs {

    fun v(message: String) {
        ReplayModule.replayDependencies.replayLogger.logVerboseInternal(message)
    }

    fun d(message: String) {
        ReplayModule.replayDependencies.replayLogger.logDebugInternal(message)
    }

    fun e(e: Throwable?, message: String) {
        ReplayModule.replayDependencies.replayLogger.logErrorInternal(message, e)
    }
}

/**
 * Sets up and controls the replay feature
 * @param errorHandler allows to report internal errors to our backend
 * @param replayLogger the callback to use to report replay captured screens
 * @param sessionReplayConfiguration the configuration to use
 * @param runtime allows for the feature to be remotely disabled
 */
class ReplayModule(
    errorHandler: ErrorHandler,
    internal val replayLogger: ReplayLogger,
    sessionReplayConfiguration: SessionReplayConfiguration,
    val runtime: Runtime,
) {
    private lateinit var replayCaptureController: ReplayCaptureController

    init {
        replayDependencies = ReplayDependencies(
            errorHandler = errorHandler,
            replayLogger = replayLogger,
            sessionReplayConfiguration = sessionReplayConfiguration,
        )
    }

    /**
     * Creates the replay feature
     */
    fun create(context: Context) {
        replayCaptureController = ReplayCaptureController(
            sessionReplayConfiguration = replayDependencies.sessionReplayConfiguration,
            runtime = runtime,
        )
        replayDependencies.displayManager.init(context)
    }

    /**
     * Starts capturing screens periodically using the given configuration
     */
    fun start() {
        replayCaptureController.start()
    }

    /**
     * Stops capturing screens
     */
    fun stop() {
        replayCaptureController.stop()
    }

    companion object {
        // TODO(murki): Refactor dependencies to not rely on singleton god state
        internal lateinit var replayDependencies: ReplayDependencies
    }
}
