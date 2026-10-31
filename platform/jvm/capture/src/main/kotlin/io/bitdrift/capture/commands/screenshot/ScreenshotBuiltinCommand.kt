// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand

/**
 * The built-in `take_screenshot` command: one JPEG attachment. It takes no arguments and ignores
 * any it is sent; the backend validates arguments against the command's definition before sending.
 */
internal class ScreenshotBuiltinCommand(
    private val capturer: IScreenshotCapturer,
) : IBuiltinCommand {
    override val type: BuiltinCommandType = BuiltinCommandType.TAKE_SCREENSHOT
    override val artifactType: String = ARTIFACT_TYPE
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult = capturer.capture()

    companion object {
        const val ARTIFACT_TYPE = "screenshot"
    }
}
