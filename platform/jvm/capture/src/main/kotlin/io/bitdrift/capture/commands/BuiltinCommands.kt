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
