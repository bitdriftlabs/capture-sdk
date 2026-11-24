// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.common.IWindowManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Takes a screenshot through [ScreenshotCaptureEngine] on the calling command's thread.
 *
 * A cancelled command waits for capture completion or the engine's per-window timeout. Timed-out
 * copies may still finish in the platform; their bitmaps are never recycled while they could be in use.
 * The registry already runs one invocation of a command at a time, so nothing else serializes here.
 *
 * @param captureScreenshot produces the JPEG bytes or throws [ScreenshotCaptureException].
 */
internal class PixelCopyScreenshotCapturer(
    private val captureScreenshot: suspend () -> ByteArray,
) : IScreenshotCapturer {
    constructor(
        windowManager: IWindowManager,
    ) : this(ScreenshotCaptureEngine(windowManager)::captureDeviceCommandScreenshot)

    override suspend fun capture(): CommandResult =
        try {
            CommandResult.Success(CommandAttachment(withContext(NonCancellable) { captureScreenshot() }, CONTENT_TYPE))
        } catch (e: ScreenshotCaptureException) {
            // The reason travels on the failure result.
            CommandResult.Error(
                listOfNotNull("screenshot capture failed", e.message?.takeUnless { it.isBlank() }).joinToString(": "),
            )
        }

    private companion object {
        const val CONTENT_TYPE = "image/jpeg"
    }
}
