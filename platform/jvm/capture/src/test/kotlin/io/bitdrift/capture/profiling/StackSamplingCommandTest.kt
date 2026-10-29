// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pins the built-in stack sampling handler: argument defaults and clamps, fail-fast when the process
 * is not profileable, the trace moved into SDK storage and attached by path, and cleanup after a
 * cancelled capture. Handlers run on [Dispatchers.Unconfined] so they reach the profiler before the
 * test continues and resume inline when the fake profiler reports.
 */
@OptIn(ExperimentalBitdriftApi::class)
class StackSamplingCommandTest {
    private class FakeProfiler : IProfiler {
        val requests = mutableListOf<ProfileRequest>()
        val callbacks = mutableListOf<(ProfileOutcome) -> Unit>()
        val cancelled = mutableListOf<Long>()
        var failure: RuntimeException? = null

        override fun request(
            request: ProfileRequest,
            callback: (ProfileOutcome) -> Unit,
        ): IProfilingCancel {
            requests += request
            failure?.let { throw it }
            callbacks += callback
            return IProfilingCancel { cancelled += request.requestId }
        }

        fun complete(outcome: ProfileOutcome) = callbacks.removeAt(0)(outcome)
    }

    private val profiler = FakeProfiler()
    private var profileable = true
    private val systemDirectory: File = Files.createTempDirectory("profiling-system").toFile()
    private val traceDirectory: File = File(Files.createTempDirectory("profiling-sdk").toFile(), "profiling")
    private val command =
        StackSamplingCommand(
            profiler = profiler,
            profileability = { profileable },
            traceDirectory = traceDirectory,
        )

    private fun invocation(vararg arguments: Pair<String, CommandArgument>) =
        CommandInvocation(BuiltinCommandType.STACK_SAMPLING_TRACE.key, "command", "session", arguments.toMap())

    private fun int(value: Long) = CommandArgument.Int64(value)

    private fun text(value: String) = CommandArgument.Text(value)

    private fun systemTrace(): File = File(systemDirectory, "profile_2026-10-24.perfetto-stack-sample").apply { writeText("trace") }

    @Test
    fun defaultsApplyAndTheTraceIsMovedAndAttachedByPath(): Unit =
        runBlocking {
            val trace = systemTrace()
            val result = async(Dispatchers.Unconfined) { command.execute(invocation()) }

            assertThat(profiler.requests).containsExactly(
                ProfileRequest(1, ProfileType.STACK_SAMPLING, 3_000, 100, 2_048, "bitdrift"),
            )
            profiler.complete(ProfileOutcome.Saved(trace.path))

            val success = result.await() as CommandResult.Success
            val expected = File(traceDirectory, trace.name)
            assertThat(success.attachment?.file).isEqualTo(expected)
            assertThat(success.attachment?.contentType).isEqualTo("application/vnd.bitdrift.perfetto-trace")
            assertThat(expected).hasContent("trace")
            assertThat(trace).doesNotExist()
            assertThat(success.context).isEqualTo(
                mapOf(
                    "profiling_type" to "STACK_SAMPLING",
                    "duration_ms" to "3000",
                    "frequency_hz" to "100",
                    "buffer_size_kb" to "2048",
                ),
            )
        }

    @Test
    fun argumentsAreClamped(): Unit =
        runBlocking {
            val result =
                async(Dispatchers.Unconfined) {
                    command.execute(
                        invocation(
                            "duration_ms" to int(60_000),
                            "frequency_hz" to int(1),
                            "buffer_size_kb" to int(100_000),
                        ),
                    )
                }

            assertThat(profiler.requests.single()).isEqualTo(
                ProfileRequest(1, ProfileType.STACK_SAMPLING, 10_000, 10, 16_384, "bitdrift"),
            )
            profiler.complete(ProfileOutcome.Saved(systemTrace().path))
            val success = result.await() as CommandResult.Success
            assertThat(success.context["duration_ms"]).isEqualTo("10000")
            assertThat(success.context).doesNotContainKey("tag")
        }

    @Test
    fun invalidArgumentsFailBeforeTheProfilerIsAsked(): Unit =
        runBlocking {
            val invalid =
                listOf(
                    invocation("duration_ms" to text("fast")),
                    invocation("frequency_hz" to text("often")),
                    invocation("buffer_size_kb" to text("big")),
                )
            for (arguments in invalid) {
                val error = command.execute(arguments) as CommandResult.Error
                assertThat(error.code).isEqualTo(CommandErrorCode.InvalidArguments)
                assertThat(error.title).isEqualTo("invalid_arguments")
            }
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun nonProfileableProcessFailsWithoutCallingTheProfiler(): Unit =
        runBlocking {
            profileable = false

            val error = command.execute(invocation()) as CommandResult.Error

            assertThat(error.title).isEqualTo("not_profileable")
            assertThat(error.code).isEqualTo(CommandErrorCode.HandlerFailed)
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun profilerFailureIsReportedWithItsCodeAndName(): Unit =
        runBlocking {
            val result = async(Dispatchers.Unconfined) { command.execute(invocation()) }
            profiler.complete(ProfileOutcome.Failed(2, "FAILED_RATE_LIMIT_PROCESS", "rate limited"))

            val error = result.await() as CommandResult.Error
            assertThat(error.title).isEqualTo("FAILED_RATE_LIMIT_PROCESS")
            assertThat(error.description).isEqualTo("rate limited")
            assertThat(error.context).isEqualTo(
                mapOf("profiling_error_code" to "2", "profiling_error_name" to "FAILED_RATE_LIMIT_PROCESS"),
            )
        }

    @Test
    fun requestFailureIsReportedInsteadOfEscaping(): Unit =
        runBlocking {
            profiler.failure = IllegalStateException("profiling service unavailable")

            val error = command.execute(invocation()) as CommandResult.Error

            assertThat(error.title).isEqualTo("request_failed")
            assertThat(error.description).contains("profiling service unavailable")
        }

    @Test
    fun cancellationStopsTheCaptureAndDeletesALateTrace(): Unit =
        runBlocking {
            val trace = systemTrace()
            val job = launch(Dispatchers.Unconfined) { command.execute(invocation()) }
            assertThat(profiler.requests).hasSize(1)

            job.cancel()
            assertThat(profiler.cancelled).containsExactly(1L)

            // The platform still saves the trace; nobody will upload it, so it is removed at once.
            profiler.complete(ProfileOutcome.Saved(trace.path))
            assertThat(trace).doesNotExist()
            assertThat(traceDirectory.listFiles().orEmpty()).isEmpty()
        }

    @Test
    fun traceSavedBeforeACancelledResumptionRunsIsDeleted(): Unit =
        runBlocking {
            val trace = systemTrace()
            // A dispatcher that holds resumptions until asked, like a busy worker thread.
            val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
            val executor = java.util.concurrent.Executor { queued.add(it) }
            val dispatcher = executor.asCoroutineDispatcher()
            val job = launch(dispatcher) { command.execute(invocation()) }
            generateSequence { queued.poll() }.forEach { it.run() }
            assertThat(profiler.requests).hasSize(1)

            // The profiler reports, which schedules the resumption; the command is cancelled before it runs.
            profiler.complete(ProfileOutcome.Saved(trace.path))
            job.cancel()
            generateSequence { queued.poll() }.forEach { it.run() }

            assertThat(trace).doesNotExist()
            assertThat(traceDirectory.listFiles().orEmpty()).isEmpty()
        }

    @Test
    fun savedTraceThatCannotBeMovedKeepsTheSystemPath(): Unit =
        runBlocking {
            val result = async(Dispatchers.Unconfined) { command.execute(invocation()) }
            val missing = File(systemDirectory, "vanished.perfetto-stack-sample")
            profiler.complete(ProfileOutcome.Saved(missing.path))

            val success = result.await() as CommandResult.Success
            assertThat(success.attachment?.file).isEqualTo(missing)
        }

    @Test
    fun firstCaptureSweepsOldOrphansButKeepsRecentTracesAndSweepsOnlyOnce(): Unit =
        runBlocking {
            traceDirectory.mkdirs()
            val orphan = File(traceDirectory, "orphan.perfetto-stack-sample").apply { writeText("old") }
            orphan.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)
            val recent = File(traceDirectory, "recent.perfetto-stack-sample").apply { writeText("new") }

            val first = async(Dispatchers.Unconfined) { command.execute(invocation()) }
            assertThat(orphan).doesNotExist()
            assertThat(recent).exists()
            profiler.complete(ProfileOutcome.Failed(0, "cancelled", "cancelled"))
            first.await()

            // A later orphan is not swept again in this process.
            val later = File(traceDirectory, "later.perfetto-stack-sample").apply { writeText("old") }
            later.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)
            val second = async(Dispatchers.Unconfined) { command.execute(invocation()) }
            assertThat(later).exists()
            profiler.complete(ProfileOutcome.Failed(0, "cancelled", "cancelled"))
            second.await()
        }
}
