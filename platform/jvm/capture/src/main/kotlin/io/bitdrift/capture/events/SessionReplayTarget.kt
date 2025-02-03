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
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.replay.IReplayLogger
import io.bitdrift.capture.replay.IScreenshotLogger
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ScreenshotCaptureMetrics
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.threading.CaptureDispatcher
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// Controls the replay feature
internal class SessionReplayTarget(
    configuration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    context: Context,
    private val logger: LoggerImpl,
    mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : ISessionReplayTarget,
    IReplayLogger,
    IScreenshotLogger {
    // TODO(Augustyniak): Make non nullable and pass at initialization time after
    //  `sessionReplayTarget` argument is moved from logger creation time to logger start time.
    //  Refer to TODO in `LoggerImpl` for more details.
    internal var runtime: Runtime? = null
    private val sessionReplayController: SessionReplayController =
        SessionReplayController(
            errorHandler,
            this,
            this,
            configuration,
            context,
            mainThreadHandler,
            CaptureDispatcher.SessionReplay.executorService
        )

    override fun captureScreen() {
        val skipReplayComposeViews =
            !(
                runtime?.isEnabled(RuntimeFeature.SESSION_REPLAY_COMPOSE)
                    ?: RuntimeFeature.SESSION_REPLAY_COMPOSE.defaultValue
            )
        sessionReplayController.captureScreen(skipReplayComposeViews)
    }

    override fun onScreenCaptured(
        encodedScreen: ByteArray,
        screen: FilteredCapture,
        metrics: ReplayCaptureMetrics,
    ) {
        val fields =
            buildMap {
                put("screen", encodedScreen.toFieldValue())
                putAll(metrics.toMap().toFields())
            }

        logger.logSessionReplayScreen(fields, metrics.parseDuration)
    }

    override fun captureScreenshot() {
        sessionReplayController.captureScreenshot()
    }

    override fun onScreenshotCaptured(
        compressedScreen: ByteArray,
        metrics: ScreenshotCaptureMetrics,
    ) {
        val fields =
            buildMap {
                put("screen_px", compressedScreen.toFieldValue())
                putAll(metrics.toMap().toFields())
            }
        logger.logSessionReplayScreenshot(fields, metrics.screenshotTimeMs.toDuration(DurationUnit.MILLISECONDS))
    }

    override fun logVerboseInternal(
        message: String,
        fields: Map<String, String>?,
    ) {
        logger.log(LogType.INTERNALSDK, LogLevel.TRACE, fields.toFields()) { message }
    }

    override fun logDebugInternal(
        message: String,
        fields: Map<String, String>?,
    ) {
        logger.log(LogType.INTERNALSDK, LogLevel.DEBUG, fields.toFields()) { message }
    }

    override fun logErrorInternal(
        message: String,
        e: Throwable?,
        fields: Map<String, String>?,
    ) {
        logger.log(LogType.INTERNALSDK, LogLevel.ERROR, logger.extractFields(fields, e)) { message }
    }
}
