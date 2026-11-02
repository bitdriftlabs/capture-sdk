// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling.stacksampling

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import io.bitdrift.capture.commands.InvalidCommandArgumentException
import io.bitdrift.capture.commands.argument
import io.bitdrift.capture.commands.profiling.IProfileabilityChecker
import io.bitdrift.capture.commands.profiling.IProfiler
import io.bitdrift.capture.commands.profiling.ProfileOutcome
import io.bitdrift.capture.commands.profiling.ProfileRequest
import io.bitdrift.capture.commands.profiling.ProfileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * The built-in `stack_sampling_trace` command: a platform handler the SDK registers for itself under
 * the well-known command type, so the core runs it like an application-defined command. It records a Perfetto
 * stack sampling trace of this process through the platform profiler and returns the trace file as
 * the command attachment.
 *
 * Every argument is optional: `duration_ms`, `frequency_hz` and `buffer_size_kb` fall back to the
 * defaults below, and the platform clamps them to its own bounds.
 * Each capture carries the profiler tag [TAG], which marks it as the SDK's rather than one the app
 * requested itself; the platform also puts the tag in the trace's file name.
 */
internal class StackSamplingCommand(
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
) : IBuiltinCommand {
    override val type: BuiltinCommandType = BuiltinCommandType.STACK_SAMPLING_TRACE
    override val artifactType: String = ARTIFACT_TYPE

    // No timeout of its own: the core applies its runtime-configured `device_command.execution_timeout_ms`.
    override val timeoutMs: Long? = null

    override suspend fun execute(scope: CommandScope): CommandResult = scope.sample()

    /** The command body as a scope extension, so argument access and result helpers stay unqualified. */
    private suspend fun CommandScope.sample(): CommandResult {
        val request =
            try {
                parse()
            } catch (e: InvalidCommandArgumentException) {
                return error("invalid_arguments", e.message, code = CommandErrorCode.InvalidArguments)
            }
        if (!profileability.isProfileable()) {
            return error(
                "not_profileable",
                "the app is not profileable; set <profileable android:enabled=\"true\" /> or use a debuggable build",
            )
        }
        return when (val outcome = capture(request)) {
            is ProfileOutcome.Saved -> {
                success(attachment = CommandAttachment.file(File(outcome.path), CONTENT_TYPE))
            }
            is ProfileOutcome.Failed ->
                error(
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
     * Fills in the defaults. A value of the wrong type throws; one beyond an Int saturates, and the
     * platform clamps it like any other.
     */
    private fun CommandScope.parse(): ProfileRequest =
        ProfileRequest(
            type = ProfileType.STACK_SAMPLING,
            durationMs = intArgument("duration_ms", DEFAULT_DURATION_MS),
            frequencyHz = intArgument("frequency_hz", DEFAULT_FREQUENCY_HZ),
            bufferSizeKb = intArgument("buffer_size_kb", DEFAULT_BUFFER_KB),
            tag = TAG,
        )

    private fun CommandScope.intArgument(
        name: String,
        default: Int,
    ): Int = if (name in arguments) argument<Long>(name).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() else default

    /**
     * Runs the capture and suspends until the profiler reports. Cancelling the coroutine stops the
     * capture; a trace the profiler still saves afterwards is deleted, since nobody would upload it.
     * `resume(value, onCancellation)` is the documented way to release a resource whose resumption
     * is lost to cancellation; it is still marked experimental in kotlinx-coroutines 1.7.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun capture(request: ProfileRequest): ProfileOutcome =
        suspendCancellableCoroutine { continuation ->
            val cancel =
                profiler.request(request) { outcome ->
                    // The outcome is delivered unless the coroutine was cancelled before, or while,
                    // it resumes; then a saved trace, which nobody would upload, is deleted.
                    continuation.resume(outcome) { (outcome as? ProfileOutcome.Saved)?.let { File(it.path).delete() } }
                }
            continuation.invokeOnCancellation { cancel.cancel() }
        }

    companion object {
        /** The artifact contract type ID of the trace attachment. */
        const val ARTIFACT_TYPE = "stack_sampling_trace"

        const val CONTENT_TYPE = "application/vnd.bitdrift.perfetto-trace"

        /**
         * The profiler tag on every SDK capture. The system records it in the trace file name and
         * reports it through `ProfilingResult.getTag()`, so an app that also uses the profiler can
         * tell the SDK's captures from its own.
         */
        const val TAG = "bitdrift"

        const val DEFAULT_DURATION_MS = 3_000
        const val DEFAULT_FREQUENCY_HZ = 100
        const val DEFAULT_BUFFER_KB = 2_048
    }
}
