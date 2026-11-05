// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling.stacksampling

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import io.bitdrift.capture.commands.profiling.ProfileCommandRunner
import io.bitdrift.capture.commands.profiling.ProfileRequest
import io.bitdrift.capture.commands.profiling.intArgument

/**
 * The built-in `stack_sampling_trace` command: a platform handler the SDK registers for itself under
 * the well-known command type, so the core runs it like an application-defined command. It records a Perfetto
 * stack sampling trace of this process through the platform profiler and returns the trace file as
 * the command attachment.
 *
 * The backend always sends `duration_ms`, `frequency_hz` and `buffer_size_kb`, filling in their
 * defaults; a missing or mistyped one fails the invocation with `invalid_arguments`. The platform
 * clamps each value to its own bounds.
 */
internal class StackSamplingCommand(
    private val runner: ProfileCommandRunner,
) : IBuiltinCommand {
    override val type: BuiltinCommandType = BuiltinCommandType.STACK_SAMPLING_TRACE
    override val artifactType: String = ARTIFACT_TYPE

    // No timeout of its own: the core applies its runtime-configured `device_command.execution_timeout_ms`.
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult = runner.run(scope, scope.parse())

    /** Reads the arguments; a missing or mistyped one throws before the profiler is asked. */
    private fun CommandScope.parse(): ProfileRequest =
        ProfileRequest.StackSampling(
            durationMs = intArgument("duration_ms"),
            frequencyHz = intArgument("frequency_hz"),
            bufferSizeKb = intArgument("buffer_size_kb"),
            tag = ProfileCommandRunner.TAG,
        )

    private companion object {
        /** The artifact contract type ID of the trace attachment. */
        const val ARTIFACT_TYPE = "stack_sampling_trace"
    }
}
