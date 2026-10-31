// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
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
 * @param captureScreenshot produces the JPEG bytes or throws [ScreenshotCaptureException].
 */
internal class PixelCopyScreenshotCapturer(
    private val captureScreenshot: suspend () -> ByteArray,
) : IScreenshotCapturer {
    constructor(
        windowManager: IWindowManager,
        mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    ) : this(ScreenshotCaptureEngine(mainThreadHandler, windowManager)::captureDeviceCommandScreenshot)

    override suspend fun capture(): CommandResult =
        try {
            CommandResult.Success(CommandAttachment(withContext(NonCancellable) { captureScreenshot() }, CONTENT_TYPE))
        } catch (e: ScreenshotCaptureException) {
            // The reason travels on the failure result.
            CommandResult.Error("screenshot capture failed", description = e.message)
        }

    companion object {
        const val CONTENT_TYPE = "image/jpeg"
    }
}
