// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.FilteredCapture

// Controls the replay feature
internal class ReplayScreenLogger(
    errorHandler: ErrorHandler,
    private val context: Context,
    private val logger: LoggerImpl,
    private val processLifecycleOwner: LifecycleOwner,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    runtime: Runtime,
    configuration: SessionReplayConfiguration,
) : LifecycleEventObserver, ReplayLogger {

    private val replayModule: ReplayModule = ReplayModule(errorHandler, this, configuration, runtime)

    fun start() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.addObserver(this)
        }
    }

    fun stop() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.removeObserver(this)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> replayModule.create(context)
            Lifecycle.Event.ON_START -> replayModule.start()
            Lifecycle.Event.ON_STOP -> replayModule.stop()
            else -> {}
        }
    }

    override fun onScreenCaptured(encodedScreen: ByteArray, screen: FilteredCapture, metrics: EncodedScreenMetrics) {
        val fields = buildMap<String, FieldValue> {
            put("screen", encodedScreen.toFieldValue())
            putAll(metrics.toMap().toFields())
        }

        logger.logSessionReplay(fields, metrics.parseDuration)
    }

    override fun logVerboseInternal(message: String, fields: Map<String, String>?) {
        logger.log(LogType.INTERNALSDK, LogLevel.TRACE, fields.toFields()) { message }
    }

    override fun logDebugInternal(message: String, fields: Map<String, String>?) {
        logger.log(LogType.INTERNALSDK, LogLevel.DEBUG, fields.toFields()) { message }
    }

    override fun logErrorInternal(message: String, e: Throwable?, fields: Map<String, String>?) {
        logger.log(LogType.INTERNALSDK, LogLevel.ERROR, logger.extractFields(fields, e)) { message }
    }
}
