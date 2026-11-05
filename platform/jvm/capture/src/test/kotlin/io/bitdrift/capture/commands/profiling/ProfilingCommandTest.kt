// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

import io.bitdrift.capture.commands.BuiltinCommandArguments
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandArgumentTypeException
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.HeapProfileTraceArguments
import io.bitdrift.capture.commands.MissingCommandArgumentException
import io.bitdrift.capture.commands.StackSamplingTraceArguments
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
 * Pins the profiling built-ins: each command's registration and generated arguments, and the
 * shared flow (through stack sampling): fail-fast when the process is not profileable, the trace
 * attached by path, and cleanup after a cancelled capture. Handlers run on [Dispatchers.Unconfined]
 * so they reach the profiler before the test continues and resume inline when the fake profiler
 * reports.
 */
@OptIn(ExperimentalBitdriftApi::class)
class ProfilingCommandTest {
    private class FakeProfiler : IProfiler {
        val requests = mutableListOf<BuiltinCommandArguments>()
        val callbacks = mutableListOf<(ProfileOutcome) -> Unit>()
        val cancelled = mutableListOf<BuiltinCommandArguments>()
        var failure: RuntimeException? = null

        override fun request(
            arguments: BuiltinCommandArguments,
            callback: (ProfileOutcome) -> Unit,
        ): ICancellable {
            requests += arguments
            failure?.let { throw it }
            callbacks += callback
            return ICancellable { cancelled += arguments }
        }

        fun complete(outcome: ProfileOutcome) = callbacks.removeAt(0)(outcome)
    }

    private val profiler = FakeProfiler()
    private var profileable = true
    private val systemDirectory: File = Files.createTempDirectory("profiling").toFile()
    private val commands =
        listOf(BuiltinCommandType.STACK_SAMPLING_TRACE, BuiltinCommandType.HEAP_PROFILE_TRACE)
            .associateWith { ProfilingCommand(it, profiler) { profileable } }
    private val command = commands.getValue(BuiltinCommandType.STACK_SAMPLING_TRACE)
    private val heapProfile = commands.getValue(BuiltinCommandType.HEAP_PROFILE_TRACE)

    /** A stack sampling invocation with every argument the backend sends, overridden by [arguments]. */
    private fun invocation(vararg arguments: Pair<String, CommandArgument>) =
        invocationOf(
            BuiltinCommandType.STACK_SAMPLING_TRACE,
            REQUIRED_ARGUMENTS.getValue(BuiltinCommandType.STACK_SAMPLING_TRACE) + arguments,
        )

    private fun heapInvocation(vararg arguments: Pair<String, CommandArgument>) =
        invocationOf(BuiltinCommandType.HEAP_PROFILE_TRACE, REQUIRED_ARGUMENTS.getValue(BuiltinCommandType.HEAP_PROFILE_TRACE) + arguments)

    private fun invocationOf(
        type: BuiltinCommandType,
        arguments: Map<String, CommandArgument>,
    ) = CommandInvocation(type.key, "command", "session", arguments)

    private fun int(value: Long) = CommandArgument.Int64(value)

    private fun text(value: String) = CommandArgument.Text(value)

    /** Writes a trace the way the platform does, after the command has made its request. */
    private fun systemTrace(name: String = "profile_bitdrift_2026-10-24.perfetto-stack-sample"): File =
        File(systemDirectory, name).apply { writeText("trace") }

    @Test
    fun eachCommandRegistersUnderItsKeyAsArtifactTypeWithoutATimeout() {
        for (command in commands.values) {
            assertThat(command.artifactType).isEqualTo(command.type.key)
            assertThat(command.timeoutMs).isNull()
        }
    }

    @Test
    fun argumentsAreRequestedAndTheTraceIsAttachedByPath(): Unit =
        runBlocking {
            val result = async(Dispatchers.Unconfined) { command.execute(invocation()) }

            assertThat(profiler.requests).containsExactly(
                StackSamplingTraceArguments(3_000, 100, 2_048),
            )
            val trace = systemTrace()
            profiler.complete(ProfileOutcome.Saved(trace.path))

            val success = result.await() as CommandResult.Success
            assertThat(success.attachment?.file).isEqualTo(trace.absoluteFile)
            assertThat(success.attachment?.contentType).isEqualTo("application/vnd.bitdrift.perfetto-trace")
            assertThat(trace).hasContent("trace")
            assertThat(success.context).isEmpty()
        }

    @Test
    fun argumentsPassThroughForThePlatformToClamp(): Unit =
        runBlocking {
            val result =
                async(Dispatchers.Unconfined) {
                    command.execute(
                        invocation(
                            "duration_ms" to int(Int.MAX_VALUE + 1L),
                            "frequency_hz" to int(0),
                            "buffer_size_kb" to int(100_000),
                        ),
                    )
                }

            assertThat(profiler.requests.single()).isEqualTo(
                StackSamplingTraceArguments(Int.MAX_VALUE + 1L, 0, 100_000),
            )
            profiler.complete(ProfileOutcome.Saved(systemTrace().path))
            result.await()
        }

    @Test
    fun heapProfileArgumentsPassThroughForThePlatformToClamp(): Unit =
        runBlocking {
            async(Dispatchers.Unconfined) { heapProfile.execute(heapInvocation()) }
            async(Dispatchers.Unconfined) {
                heapProfile.execute(
                    heapInvocation(
                        "duration_ms" to int(Int.MAX_VALUE + 1L),
                        "sampling_interval_bytes" to int(Long.MAX_VALUE),
                        "track_java_allocations" to CommandArgument.Bool(false),
                    ),
                )
            }

            assertThat(profiler.requests).containsExactly(
                HeapProfileTraceArguments(5_000, 8_192, 65_536, true),
                HeapProfileTraceArguments(Int.MAX_VALUE + 1L, 8_192, Long.MAX_VALUE, false),
            )
            repeat(2) { profiler.complete(ProfileOutcome.Failed(0, "unsupported", "")) }
        }

    @Test
    fun aMissingArgumentFailsBeforeTheProfilerIsAsked(): Unit =
        runBlocking {
            for ((type, required) in REQUIRED_ARGUMENTS) {
                for (name in required.keys) {
                    val thrown = runCatching { commands.getValue(type).execute(invocationOf(type, required - name)) }.exceptionOrNull()

                    assertThat(thrown).isInstanceOf(MissingCommandArgumentException::class.java)
                    assertThat((thrown as MissingCommandArgumentException).name).isEqualTo(name)
                }
            }
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun aMistypedArgumentFailsBeforeTheProfilerIsAsked(): Unit =
        runBlocking {
            for ((type, required) in REQUIRED_ARGUMENTS) {
                for (name in required.keys) {
                    val thrown =
                        runCatching { commands.getValue(type).execute(invocationOf(type, required + (name to text("fast")))) }
                            .exceptionOrNull()

                    assertThat(thrown).isInstanceOf(CommandArgumentTypeException::class.java)
                    assertThat(thrown).hasMessageContaining(name)
                }
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
    fun aRequestFailurePropagatesAsIs(): Unit =
        runBlocking {
            profiler.failure = IllegalStateException("profiling service unavailable")

            val thrown = runCatching { command.execute(invocation()) }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            assertThat(thrown).hasMessage("profiling service unavailable")
        }

    @Test
    fun cancellationStopsTheCaptureAndDeletesALateTrace(): Unit =
        runBlocking {
            val job = launch(Dispatchers.Unconfined) { command.execute(invocation()) }
            assertThat(profiler.requests).hasSize(1)
            val trace = systemTrace()

            job.cancel()
            assertThat(profiler.cancelled).containsExactlyElementsOf(profiler.requests)

            // The platform still saves the trace; nobody will upload it, so it is removed at once.
            profiler.complete(ProfileOutcome.Saved(trace.path))
            assertThat(trace).doesNotExist()
        }

    @Test
    fun traceSavedBeforeACancelledResumptionRunsIsDeleted(): Unit =
        runBlocking {
            // A dispatcher that holds resumptions until asked, like a busy worker thread.
            val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
            val executor = java.util.concurrent.Executor { queued.add(it) }
            val dispatcher = executor.asCoroutineDispatcher()
            val job = launch(dispatcher) { command.execute(invocation()) }
            generateSequence { queued.poll() }.forEach { it.run() }
            assertThat(profiler.requests).hasSize(1)
            val trace = systemTrace()

            // The profiler reports, which schedules the resumption; the command is cancelled before it runs.
            profiler.complete(ProfileOutcome.Saved(trace.path))
            job.cancel()
            generateSequence { queued.poll() }.forEach { it.run() }

            assertThat(trace).doesNotExist()
        }

    private companion object {
        /** Every argument the backend sends, with the proto's defaults. */
        val REQUIRED_ARGUMENTS: Map<BuiltinCommandType, Map<String, CommandArgument>> =
            mapOf(
                BuiltinCommandType.STACK_SAMPLING_TRACE to
                    mapOf(
                        "duration_ms" to CommandArgument.Int64(3_000),
                        "frequency_hz" to CommandArgument.Int64(100),
                        "buffer_size_kb" to CommandArgument.Int64(2_048),
                    ),
                BuiltinCommandType.HEAP_PROFILE_TRACE to
                    mapOf(
                        "duration_ms" to CommandArgument.Int64(5_000),
                        "buffer_size_kb" to CommandArgument.Int64(8_192),
                        "sampling_interval_bytes" to CommandArgument.Int64(65_536),
                        "track_java_allocations" to CommandArgument.Bool(true),
                    ),
            )
    }
}
