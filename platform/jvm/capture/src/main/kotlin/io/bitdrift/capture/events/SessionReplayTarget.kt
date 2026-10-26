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
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
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
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.SessionReplayController
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.threading.CaptureDispatchers
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Controls the replay feature
internal class SessionReplayTarget(
    configuration: SessionReplayConfiguration,
    errorHandler: ErrorHandler,
    context: Context,
    private val logger: IInternalLogger,
    mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    windowManager: IWindowManager,
    private val captureScreenshot: (() -> Unit)? = null,
) : ISessionReplayTarget,
    IReplayLogger,
    IScreenshotLogger {
    // TODO(Augustyniak): Make non nullable and pass at initialization time after
    //  `sessionReplayTarget` argument is moved from logger creation time to logger start time.
    //  Refer to TODO in `LoggerImpl` for more details.
    internal var runtime: Runtime? = null
    private val screenshotCaptureLock = Any()
    private var screenshotCaptureInProgress = false
    private var commandScreenshotCompletion: CancellableContinuation<CommandResult>? = null
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

    override fun onDeviceCommandScreenshotCaptured(compressedScreen: ByteArray) {
        val completion =
            synchronized(screenshotCaptureLock) {
                commandScreenshotCompletion.also {
                    commandScreenshotCompletion = null
                    screenshotCaptureInProgress = false
                }
            }
        if (completion != null) {
            // JPEG SOI (FF D8) and EOI (FF D9) markers need four bytes total; the screenshot
            // artifact contract caps the entire JPEG at 2 MiB (1024 bytes per KiB).
            val valid =
                compressedScreen.size in 4..(2 * 1024 * 1024) &&
                    compressedScreen[0] == 0xff.toByte() &&
                    compressedScreen[1] == 0xd8.toByte() &&
                    compressedScreen[compressedScreen.lastIndex - 1] == 0xff.toByte() &&
                    compressedScreen.last() == 0xd9.toByte()
            completion.resume(
                if (valid) {
                    CommandResult.Success(CommandAttachment(compressedScreen, "image/jpeg"))
                } else {
                    CommandResult.Error("invalid or unavailable screenshot")
                },
            )
            return
        }
        logger.logInternal(LogType.INTERNALSDK, LogLevel.ERROR) {
            "received an uncorrelated device command screenshot"
        }
    }

    suspend fun captureCommandScreenshot(): CommandResult =
        suspendCancellableCoroutine { completion ->
            synchronized(screenshotCaptureLock) {
                if (screenshotCaptureInProgress) {
                    completion.resume(CommandResult.Error("screenshot capture already in progress"))
                    return@suspendCancellableCoroutine
                }
                screenshotCaptureInProgress = true
                commandScreenshotCompletion = completion
            }
            try {
                val capture = captureScreenshot
                if (capture != null) capture() else sessionReplayController.captureDeviceCommandScreenshot()
            } catch (_: Exception) {
                val pending =
                    synchronized(screenshotCaptureLock) {
                        if (commandScreenshotCompletion !== completion) {
                            false
                        } else {
                            commandScreenshotCompletion = null
                            screenshotCaptureInProgress = false
                            true
                        }
                    }
                if (pending) completion.resume(CommandResult.Error("screenshot capture failed"))
            }
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
}
