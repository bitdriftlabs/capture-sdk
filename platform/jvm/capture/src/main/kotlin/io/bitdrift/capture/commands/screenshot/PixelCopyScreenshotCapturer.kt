// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.threading.CaptureDispatchers
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import kotlin.coroutines.resume

/**
 * Adapts the callback-based [ScreenshotCaptureEngine] to a suspending capture. One physical capture
 * runs at a time: a cancelled caller releases the coroutine but keeps the latch until PixelCopy
 * reports, so two captures never overlap.
 *
 * @param capture starts one capture and delivers the JPEG bytes, or empty bytes on failure.
 */
internal class PixelCopyScreenshotCapturer(
    private val capture: ((ByteArray) -> Unit) -> Unit,
) : IScreenshotCapturer {
    constructor(
        windowManager: IWindowManager,
        errorHandler: ErrorHandler,
        logger: IInternalLogger,
        mainThreadHandler: MainThreadHandler = MainThreadHandler(),
        executor: ExecutorService = CaptureDispatchers.SessionReplay.executorService,
    ) : this(
        ScreenshotCaptureEngine(errorHandler, mainThreadHandler, windowManager, executor, logger)::captureDeviceCommandScreenshot,
    )

    private val lock = Any()
    private var pending: CancellableContinuation<CommandResult>? = null

    override suspend fun capture(): CommandResult =
        suspendCancellableCoroutine { completion ->
            synchronized(lock) {
                if (pending != null) {
                    completion.resume(
                        CommandResult.Error("screenshot capture already in progress", code = CommandErrorCode.CommandAlreadyExecuting),
                    )
                    return@suspendCancellableCoroutine
                }
                pending = completion
            }
            try {
                capture { bytes ->
                    finish(
                        completion,
                        if (bytes.isNotEmpty()) {
                            CommandResult.Success(CommandAttachment(bytes, CONTENT_TYPE))
                        } else {
                            CommandResult.Error("invalid or unavailable screenshot")
                        },
                    )
                }
            } catch (_: Exception) {
                finish(completion, CommandResult.Error("screenshot capture failed"))
            }
        }

    private fun finish(
        completion: CancellableContinuation<CommandResult>,
        result: CommandResult,
    ) {
        val accepted =
            synchronized(lock) {
                if (pending !== completion) {
                    false
                } else {
                    pending = null
                    true
                }
            }
        if (accepted) completion.resume(result)
    }

    companion object {
        const val CONTENT_TYPE = "image/jpeg"
    }
}
