// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import android.content.Context
import io.bitdrift.capture.ISessionReplayTarget
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.FilteredCapture

// Controls the replay feature
internal class SessionReplayTarget(
    configuration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    context: Context,
    private val logger: LoggerImpl,
    mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : ISessionReplayTarget, ReplayLogger {
    internal var runtime: Runtime? = null
    private val replayModule: ReplayModule = ReplayModule(
        errorHandler,
        this,
        configuration,
        mainThreadHandler,
        context,
    )

    override fun captureScreen() {
        val skipReplayComposeViews = runtime?.isEnabled(RuntimeFeature.SESSION_REPLAY_COMPOSE)
            ?: RuntimeFeature.SESSION_REPLAY_COMPOSE.defaultValue
        replayModule.captureScreen(skipReplayComposeViews)
    }

    override fun captureScreenshot() {
        // This implementation is required for the SDK to support screenshot capture on Android.
        // As currently implemented, the function must emit a session replay screenshot log.
        // Without this emission, the SDK is blocked from requesting additional screenshots.
    }

    override fun onScreenCaptured(encodedScreen: ByteArray, screen: FilteredCapture, metrics: EncodedScreenMetrics) {
        val fields = buildMap<String, FieldValue> {
            put("screen", encodedScreen.toFieldValue())
            putAll(metrics.toMap().toFields())
        }

        logger.logSessionReplayScreen(fields, metrics.parseDuration)
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