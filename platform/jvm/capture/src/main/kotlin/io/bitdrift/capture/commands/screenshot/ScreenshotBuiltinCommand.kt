// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand

/** The built-in `take_screenshot` command: no arguments, one JPEG attachment. */
internal class ScreenshotBuiltinCommand(
    private val capturer: IScreenshotCapturer,
) : IBuiltinCommand {
    override val type: BuiltinCommandType = BuiltinCommandType.TAKE_SCREENSHOT
    override val artifactType: String = ARTIFACT_TYPE
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult =
        if (scope.arguments.isNotEmpty()) {
            scope.error("screenshot does not accept arguments", code = CommandErrorCode.InvalidArguments)
        } else {
            capturer.capture()
        }

    companion object {
        const val ARTIFACT_TYPE = "screenshot"
    }
}
