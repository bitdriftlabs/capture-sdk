// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.InvalidCommandArgumentException
import io.bitdrift.capture.commands.argument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * The built-in `stack_sampling_trace` command: a platform handler the SDK registers for itself under
 * the well-known command type, so the core runs it like an application-defined command. It records a Perfetto
 * stack sampling trace of this process through the platform profiler and returns the trace file as
 * the command attachment.
 *
 * Every argument is optional and validated here: `duration_ms`, `frequency_hz` and `buffer_size_kb`.
 * Each capture carries the profiler tag [TAG], which marks it as the SDK's rather than one the app
 * requested itself. A finished trace is moved into [traceDirectory], the SDK's own storage, before
 * it is returned: the system writes every profile the app ever requests into one shared directory.
 */
internal class StackSamplingCommand(
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
    private val traceDirectory: File,
    private val logger: IInternalLogger,
) {
    private val nextRequestId = AtomicLong(1)
    private val swept = AtomicBoolean(false)

    /** The handler to register as the `stack_sampling_trace` built-in. */
    val handler: suspend CommandScope.() -> CommandResult = { execute() }

    private suspend fun CommandScope.execute(): CommandResult {
        val request =
            try {
                parse()
            } catch (e: InvalidCommandArgumentException) {
                return error("invalid_arguments", e.message, code = CommandErrorCode.InvalidArguments)
            }
        if (swept.compareAndSet(false, true)) sweepOrphans()
        if (!profileability.isProfileable()) {
            return error(
                "not_profileable",
                "the app is not profileable; set <profileable android:enabled=\"true\" /> or use a debuggable build",
            )
        }
        logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) {
            "starting stack sample ${request.requestId}: ${request.durationMs} ms at ${request.frequencyHz} Hz"
        }
        return when (val outcome = capture(request)) {
            is ProfileOutcome.Saved -> {
                val trace = claim(File(outcome.path))
                logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { "stack sample ${request.requestId} saved to $trace" }
                success(
                    attachment = CommandAttachment.file(trace, CONTENT_TYPE),
                    context = request.fields(),
                )
            }
            is ProfileOutcome.Failed -> {
                logger.logInternal(LogType.INTERNALSDK, LogLevel.WARNING) {
                    "stack sample ${request.requestId} failed: ${outcome.name} (${outcome.code}) ${outcome.message}"
                }
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
    }

    /** Applies the documented defaults and clamps. A value of the wrong type throws. */
    private fun CommandScope.parse(): ProfileRequest =
        ProfileRequest(
            requestId = nextRequestId.getAndIncrement(),
            type = ProfileType.STACK_SAMPLING,
            durationMs = clamped("duration_ms", DEFAULT_DURATION_MS, MIN_DURATION_MS, MAX_DURATION_MS),
            frequencyHz = clamped("frequency_hz", DEFAULT_FREQUENCY_HZ, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ).toInt(),
            bufferSizeKb = clamped("buffer_size_kb", DEFAULT_BUFFER_KB, MIN_BUFFER_KB, MAX_BUFFER_KB).toInt(),
            tag = TAG,
        )

    private fun CommandScope.clamped(
        name: String,
        default: Long,
        min: Long,
        max: Long,
    ): Long = if (name in arguments) argument<Long>(name).coerceIn(min, max) else default

    private fun ProfileRequest.fields(): Map<String, String> =
        mapOf(
            "profiling_type" to type.name,
            "duration_ms" to durationMs.toString(),
            "frequency_hz" to frequencyHz.toString(),
            "buffer_size_kb" to bufferSizeKb.toString(),
        )

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
                try {
                    profiler.request(request) { outcome ->
                        // The outcome is delivered unless the coroutine was cancelled before, or while,
                        // it resumes; then a saved trace, which nobody would upload, is deleted.
                        continuation.resume(outcome) { (outcome as? ProfileOutcome.Saved)?.let { File(it.path).delete() } }
                    }
                } catch (e: RuntimeException) {
                    // A Binder failure in the profiling service must not take the host app down.
                    continuation.resume(ProfileOutcome.Failed(0, "request_failed", e.toString()))
                    return@suspendCancellableCoroutine
                }
            continuation.invokeOnCancellation { cancel.cancel() }
        }

    /** Moves the trace into [traceDirectory]; a trace that cannot be moved stays where the system wrote it. */
    private fun claim(source: File): File {
        val target = File(traceDirectory, source.name)
        val moved = (traceDirectory.isDirectory || traceDirectory.mkdirs()) && source.renameTo(target)
        return if (moved) target else source
    }

    /**
     * Deletes traces an earlier process left behind: a trace stays in [traceDirectory] only between
     * its move and the uploader persisting its copy, so one that is still there minutes later belongs
     * to a process that died in between. Runs once per process, on the first capture, so the SDK
     * does no disk work at startup. Recent files are left alone: a capture that finished moments ago
     * may still be read by the uploader.
     */
    private fun sweepOrphans() {
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        val orphans = traceDirectory.listFiles { file -> file.isFile && file.lastModified() < cutoff } ?: return
        val deleted = orphans.count { it.delete() }
        if (deleted > 0) {
            logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { "deleted $deleted orphaned stack sampling trace(s)" }
        }
    }

    companion object {
        /**
         * The core's execution deadline for one capture: the longest allowed sampling window
         * ([MAX_DURATION_MS]) plus the platform's start and post-processing overhead.
         */
        const val TIMEOUT_MS = 15_000L

        const val CONTENT_TYPE = "application/vnd.bitdrift.perfetto-trace"

        /**
         * The profiler tag on every SDK capture. The system records it in the trace file name and
         * reports it through `ProfilingResult.getTag()`, so an app that also uses the profiler can
         * tell the SDK's captures from its own.
         */
        const val TAG = "bitdrift"
        const val DEFAULT_DURATION_MS = 3_000L
        const val MIN_DURATION_MS = 1_000L
        const val MAX_DURATION_MS = 10_000L
        const val DEFAULT_FREQUENCY_HZ = 100L
        const val MIN_FREQUENCY_HZ = 10L
        const val MAX_FREQUENCY_HZ = 250L
        const val DEFAULT_BUFFER_KB = 2_048L
        const val MIN_BUFFER_KB = 256L
        const val MAX_BUFFER_KB = 16_384L
        const val ORPHAN_AGE_MS = 10 * 60 * 1000L
    }
}
