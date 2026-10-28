package io.bitdrift.capture.commands

internal object BuiltinCommands {
    fun register(
        registry: CommandRegistry,
        captureScreenshot: suspend () -> CommandResult,
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
        registry.registerBuiltin(trace.wireValue, trace.key, "stack_sampling_trace", timeoutMs = 15_000) {
            error("not implemented")
        }
    }
}
