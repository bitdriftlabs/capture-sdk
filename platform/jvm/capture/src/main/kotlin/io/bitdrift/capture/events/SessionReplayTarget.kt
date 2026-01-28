// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import android.content.Context
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.ISessionReplayTarget
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.combineJniFields
import io.bitdrift.capture.providers.jniFieldsOf
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.replay.IReplayLogger
import io.bitdrift.capture.replay.IScreenshotLogger
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ScreenshotCaptureMetrics
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.threading.CaptureDispatchers
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// Controls the replay feature
internal class SessionReplayTarget(
    configuration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    context: Context,
    private val logger: IInternalLogger,
    mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    windowManager: IWindowManager,
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
            CaptureDispatchers.SessionReplay.executorService,
            windowManager,
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
        logger.logSessionReplayScreen(
            buildScreenCapturedFields(encodedScreen, metrics),
            metrics.parseDuration,
        )
    }

    override fun captureScreenshot() {
        sessionReplayController.captureScreenshot()
    }

    override fun onScreenshotCaptured(
        compressedScreen: ByteArray,
        metrics: ScreenshotCaptureMetrics,
    ) {
        logger.logSessionReplayScreenshot(
            buildScreenshotCaptureFields(compressedScreen, metrics),
            metrics.screenshotTimeMs.toDuration(DurationUnit.MILLISECONDS),
        )
    }

    override fun logVerboseInternal(message: String) {
        logger.logInternal(LogType.INTERNALSDK, LogLevel.TRACE) { message }
    }

    override fun logDebugInternal(message: String) {
        logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { message }
    }

    override fun logErrorInternal(
        message: String,
        e: Throwable?,
    ) {
        logger.logInternal(
            LogType.INTERNALSDK,
            LogLevel.ERROR,
            arrayFields = ArrayFields.EMPTY,
            throwable = e,
        ) { message }
    }

    private fun buildScreenCapturedFields(
        encodedScreen: ByteArray,
        metrics: ReplayCaptureMetrics,
    ): Array<Field> =
        combineJniFields(
            metrics.toArray().toFields(),
            jniFieldsOf("screen" to encodedScreen.toFieldValue()),
        )

    private fun buildScreenshotCaptureFields(
        compressedScreen: ByteArray,
        metrics: ScreenshotCaptureMetrics,
    ): Array<Field> =
        combineJniFields(
            metrics.toArray().toFields(),
            jniFieldsOf("screen" to compressedScreen.toFieldValue()),
        )
}
