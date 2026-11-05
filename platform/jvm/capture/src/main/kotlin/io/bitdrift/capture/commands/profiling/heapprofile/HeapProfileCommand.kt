// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling.heapprofile

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import io.bitdrift.capture.commands.argument
import io.bitdrift.capture.commands.profiling.ProfileCommandRunner
import io.bitdrift.capture.commands.profiling.ProfileRequest
import io.bitdrift.capture.commands.profiling.intArgument

/**
 * The built-in `heap_profile_trace` command: records a Perfetto heap profile of this process through
 * the platform profiler and returns the trace file as the command attachment. A profile records
 * either Java heap allocations (the default) or native ones, never both.
 *
 * The backend always sends `duration_ms`, `buffer_size_kb`, `sampling_interval_bytes` and
 * `track_java_allocations`, filling in their defaults; a missing or mistyped one fails the
 * invocation with `invalid_arguments`. The platform clamps each value to its own bounds.
 */
internal class HeapProfileCommand(
    private val runner: ProfileCommandRunner,
) : IBuiltinCommand {
    override val type: BuiltinCommandType = BuiltinCommandType.HEAP_PROFILE_TRACE
    override val artifactType: String = ARTIFACT_TYPE

    // No timeout of its own: the core applies its runtime-configured `device_command.execution_timeout_ms`.
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult = runner.run(scope, scope.parse())

    /** Reads the arguments; a missing or mistyped one throws before the profiler is asked. */
    private fun CommandScope.parse(): ProfileRequest =
        ProfileRequest.HeapProfile(
            durationMs = intArgument("duration_ms"),
            bufferSizeKb = intArgument("buffer_size_kb"),
            samplingIntervalBytes = argument<Long>("sampling_interval_bytes"),
            trackJavaAllocations = argument<Boolean>("track_java_allocations"),
            tag = ProfileCommandRunner.TAG,
        )

    private companion object {
        /** The artifact contract type ID of the trace attachment. */
        const val ARTIFACT_TYPE = "heap_profile_trace"
    }
}
