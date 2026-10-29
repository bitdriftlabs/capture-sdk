// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.commands.screenshot.IScreenshotCapturer
import io.bitdrift.capture.commands.screenshot.ScreenshotBuiltinCommand

/** The catalog of built-in commands the Android SDK ships: the one place that lists them. */
internal object BuiltinCommands {
    fun make(screenshotCapturer: IScreenshotCapturer): List<IBuiltinCommand> =
        listOf(
            ScreenshotBuiltinCommand(screenshotCapturer),
            UnavailableBuiltinCommand(
                type = BuiltinCommandType.STACK_SAMPLING_TRACE,
                artifactType = "stack_sampling_trace",
                timeoutMs = 15_000,
                title = "not implemented",
            ),
        )
}
