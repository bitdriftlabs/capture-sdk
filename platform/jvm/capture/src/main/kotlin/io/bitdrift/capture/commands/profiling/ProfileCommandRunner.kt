// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.argument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * What every profiling built-in command does once it has read its arguments: fail fast when the
 * process is not profileable, run the capture through the platform profiler, and attach the saved
 * trace or report the profiler's error.
 */
internal class ProfileCommandRunner(
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
) {
    suspend fun run(
        scope: CommandScope,
        request: ProfileRequest,
    ): CommandResult {
        if (!profileability.isProfileable()) {
            return scope.error(
                "not_profileable",
                "the app is not profileable; set <profileable android:enabled=\"true\" /> or use a debuggable build",
            )
        }
        return when (val outcome = capture(request)) {
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

    internal companion object {
        /**
         * The profiler tag on every SDK capture. The system records it in the trace file name and
         * reports it through `ProfilingResult.getTag()`, so an app that also uses the profiler can
         * tell the SDK's captures from its own.
         */
        const val TAG = "bitdrift"

        private const val CONTENT_TYPE = "application/vnd.bitdrift.perfetto-trace"
    }
}

/**
 * Reads a required integer argument, throwing when it is missing or not an integer; the registry
 * turns that into `invalid_arguments`. A value beyond an Int saturates, and the platform clamps it
 * like any other.
 */
internal fun CommandScope.intArgument(name: String): Int =
    argument<Long>(name).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
