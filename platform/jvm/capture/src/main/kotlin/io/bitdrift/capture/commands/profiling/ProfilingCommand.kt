// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.profiling

import io.bitdrift.capture.commands.BuiltinCommandArguments
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import io.bitdrift.capture.commands.MemoryDumpArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * A built-in that records a Perfetto trace through the platform profiler and returns it as the
 * command attachment. [type] selects the capture and parses the arguments for [IProfiler].
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

    // Stack sampling keeps the core's runtime-configured timeout.
    override val timeoutMs: Long? =
        when (type) {
            BuiltinCommandType.HEAP_PROFILE_TRACE -> HEAP_PROFILE_TIMEOUT_MS
            BuiltinCommandType.SYSTEM_TRACE -> SYSTEM_TRACE_TIMEOUT_MS
            BuiltinCommandType.MEMORY_DUMP -> JAVA_HEAP_DUMP_TIMEOUT_MS
            else -> null
        }

    override suspend fun execute(scope: CommandScope): CommandResult {
        val arguments = checkNotNull(type.parse(scope)) { "${type.key} declares no arguments to profile with" }
        if (arguments is MemoryDumpArguments && !arguments.includeHeap) {
            return scope.error("include_heap must be true on Android", code = CommandErrorCode.InvalidArguments)
        }
        if (!profileability.isProfileable()) {
            return scope.error(
                "not_profileable: the app is not profileable; " +
                    "set <profileable android:enabled=\"true\" /> or use a debuggable build",
            )
        }
        return when (val outcome = capture(arguments)) {
            is ProfileOutcome.Saved -> {
                scope.success(attachment = CommandAttachment.file(File(outcome.path), CONTENT_TYPE))
            }
            is ProfileOutcome.Failed ->
                scope.error(
                    listOfNotNull(outcome.name, outcome.message.takeUnless { it.isBlank() }).joinToString(": "),
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

        // AOSP Configs allows up to 300 s of recording by default, plus 42 s for startup,
        // flushing, and file processing. Leave a little more time for the result callback.
        const val HEAP_PROFILE_TIMEOUT_MS = 350_000L

        // AOSP permits up to 600 s for system recording and another 32 s for startup, file
        // processing and redaction. ART dumps allow 113 s including the data source stop timeout.
        const val SYSTEM_TRACE_TIMEOUT_MS = 650_000L
        const val JAVA_HEAP_DUMP_TIMEOUT_MS = 120_000L
    }
}
