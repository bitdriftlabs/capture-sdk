// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

import io.bitdrift.capture.commands.BuiltinCommandArguments
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * A built-in that records a Perfetto trace of this process through the platform profiler and
 * returns the file as the command attachment. Only [type] differs between capture kinds: it parses
 * the arguments that [IProfiler] turns into a platform request.
 *
 * The backend always sends every argument, filling in the proto's defaults; a missing or mistyped
 * one fails the invocation with `invalid_arguments` before the profiler is asked. The platform
 * clamps each value to its own bounds.
 */
internal class ProfilingCommand(
    override val type: BuiltinCommandType,
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
) : IBuiltinCommand {
    /** Each profiling command's artifact contract type ID is its key. */
    override val artifactType: String = type.key

    // No timeout of its own: the core applies its runtime-configured `device_command.execution_timeout_ms`.
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult {
        val arguments = checkNotNull(type.parse(scope)) { "${type.key} declares no arguments to profile with" }
        if (!profileability.isProfileable()) {
            return scope.error(
                "not_profileable",
                "the app is not profileable; set <profileable android:enabled=\"true\" /> or use a debuggable build",
            )
        }
        return when (val outcome = capture(arguments)) {
            is ProfileOutcome.Saved -> {
                scope.success(attachment = CommandAttachment.file(File(outcome.path), CONTENT_TYPE))
            }
            is ProfileOutcome.Failed ->
                scope.error(
                    outcome.name,
                    outcome.message.ifEmpty { null },
                    context =
                        mapOf(
                            "profiling_error_code" to outcome.code.toString(),
                            "profiling_error_name" to outcome.name,
                        ),
                )
        }
    }

    /**
     * Runs the capture and suspends until the profiler reports. Cancelling the coroutine stops the
     * capture; a trace the profiler still saves afterwards is deleted, since nobody would upload it.
     * `resume(value, onCancellation)` is the documented way to release a resource whose resumption
     * is lost to cancellation; it is still marked experimental in kotlinx-coroutines 1.7.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun capture(arguments: BuiltinCommandArguments): ProfileOutcome =
        suspendCancellableCoroutine { continuation ->
            val cancel =
                profiler.request(arguments) { outcome ->
                    // The outcome is delivered unless the coroutine was cancelled before, or while,
                    // it resumes; then a saved trace, which nobody would upload, is deleted.
                    continuation.resume(outcome) { (outcome as? ProfileOutcome.Saved)?.let { File(it.path).delete() } }
                }
            continuation.invokeOnCancellation { cancel.cancel() }
        }

    private companion object {
        const val CONTENT_TYPE = "application/vnd.bitdrift.perfetto-trace"
    }
}
