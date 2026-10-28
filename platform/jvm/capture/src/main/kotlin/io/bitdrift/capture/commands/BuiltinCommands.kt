// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.profiling.StackSamplingCommand

internal object BuiltinCommands {
    fun register(
        registry: CommandRegistry,
        captureScreenshot: suspend () -> CommandResult,
        stackSamplingTrace: suspend CommandScope.() -> CommandResult,
    ) {
        val screenshot = BuiltinCommandType.TAKE_SCREENSHOT
        registry.registerBuiltin(screenshot.wireValue, screenshot.key, "screenshot") {
            if (arguments.isNotEmpty()) {
                error("screenshot does not accept arguments", code = CommandErrorCode.InvalidArguments)
            } else {
                captureScreenshot()
            }
        }
        val trace = BuiltinCommandType.STACK_SAMPLING_TRACE
        registry.registerBuiltin(
            trace.wireValue,
            trace.key,
            "stack_sampling_trace",
            timeoutMs = StackSamplingCommand.TIMEOUT_MS,
            handler = stackSamplingTrace,
        )
    }
}
