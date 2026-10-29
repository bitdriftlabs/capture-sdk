// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Takes a screenshot through [ScreenshotCaptureEngine] on the calling command's thread.
 *
 * The copy is not interruptible: a cancelled command still waits the few milliseconds for the
 * platform to hand the bitmap back, so no capture is ever left running behind the next invocation.
 * The registry already runs one invocation of a command at a time, so nothing else serializes here.
 *
 * @param captureScreenshot produces the JPEG bytes, or empty bytes when the capture failed.
 */
internal class PixelCopyScreenshotCapturer(
    private val captureScreenshot: suspend () -> ByteArray,
) : IScreenshotCapturer {
    constructor(
        windowManager: IWindowManager,
        errorHandler: ErrorHandler,
        logger: IInternalLogger,
        mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    ) : this(ScreenshotCaptureEngine(errorHandler, mainThreadHandler, windowManager, logger)::captureDeviceCommandScreenshot)

    override suspend fun capture(): CommandResult {
        val bytes =
            try {
                withContext(NonCancellable) { captureScreenshot() }
            } catch (_: Exception) {
                return CommandResult.Error("screenshot capture failed")
            }
        return if (bytes.isNotEmpty()) {
            CommandResult.Success(CommandAttachment(bytes, CONTENT_TYPE))
        } else {
            CommandResult.Error("invalid or unavailable screenshot")
        }
    }

    companion object {
        const val CONTENT_TYPE = "image/jpeg"
    }
}
