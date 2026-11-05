// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling.heapprofile

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandArgumentTypeException
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.MissingCommandArgumentException
import io.bitdrift.capture.commands.profiling.ICancellable
import io.bitdrift.capture.commands.profiling.IProfiler
import io.bitdrift.capture.commands.profiling.ProfileCommandRunner
import io.bitdrift.capture.commands.profiling.ProfileOutcome
import io.bitdrift.capture.commands.profiling.ProfileRequest
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pins the built-in heap profile handler's own part: its registration and the required, typed
 * arguments it turns into a request. The shared capture flow (profileability, profiler errors,
 * cancellation) is pinned through the stack sampling command, which runs the same runner.
 */
@OptIn(ExperimentalBitdriftApi::class)
class HeapProfileCommandTest {
    private class FakeProfiler : IProfiler {
        val requests = mutableListOf<ProfileRequest>()
        val callbacks = mutableListOf<(ProfileOutcome) -> Unit>()

        override fun request(
            request: ProfileRequest,
            callback: (ProfileOutcome) -> Unit,
        ): ICancellable {
            requests += request
            callbacks += callback
            return ICancellable {}
        }

        fun complete(outcome: ProfileOutcome) = callbacks.removeAt(0)(outcome)
    }

    private val profiler = FakeProfiler()
    private var profileable = true
    private val command = HeapProfileCommand(ProfileCommandRunner(profiler = profiler, profileability = { profileable }))

    /** An invocation with every argument the backend sends, overridden by [arguments]. */
    private fun invocation(vararg arguments: Pair<String, CommandArgument>) = invocationOf(REQUIRED_ARGUMENTS + arguments)

    private fun invocationOf(arguments: Map<String, CommandArgument>) =
        CommandInvocation(BuiltinCommandType.HEAP_PROFILE_TRACE.key, "command", "session", arguments)

    @Test
    fun registersUnderItsWellKnownTypeAndArtifactContract() {
        assertThat(command.type).isEqualTo(BuiltinCommandType.HEAP_PROFILE_TRACE)
        assertThat(command.artifactType).isEqualTo("heap_profile_trace")
        assertThat(command.timeoutMs).isNull()
    }

    @Test
    fun argumentsAreRequestedAndTheTraceIsAttachedByPath(): Unit =
        runBlocking {
            val result = async(Dispatchers.Unconfined) { command.execute(invocation()) }

            assertThat(profiler.requests).containsExactly(
                ProfileRequest.HeapProfile(
                    durationMs = 5_000,
                    bufferSizeKb = 8_192,
                    samplingIntervalBytes = 65_536L,
                    trackJavaAllocations = true,
                    tag = "bitdrift",
                ),
            )
            val trace =
                File(
                    Files.createTempDirectory("profiling").toFile(),
                    "profile_bitdrift.perfetto-trace",
                ).apply { writeText("trace") }
            profiler.complete(ProfileOutcome.Saved(trace.path))

            val success = result.await() as CommandResult.Success
            assertThat(success.attachment?.file).isEqualTo(trace.absoluteFile)
            assertThat(success.attachment?.contentType).isEqualTo("application/vnd.bitdrift.perfetto-trace")
        }

    @Test
    fun argumentsPassThroughForThePlatformToClamp(): Unit =
        runBlocking {
            val result =
                async(Dispatchers.Unconfined) {
                    command.execute(
                        invocation(
                            "duration_ms" to CommandArgument.Int64(Int.MAX_VALUE + 1L),
                            "buffer_size_kb" to CommandArgument.Int64(0),
                            "sampling_interval_bytes" to CommandArgument.Int64(Long.MAX_VALUE),
                            "track_java_allocations" to CommandArgument.Bool(false),
                        ),
                    )
                }

            assertThat(profiler.requests.single()).isEqualTo(
                ProfileRequest.HeapProfile(
                    durationMs = Int.MAX_VALUE,
                    bufferSizeKb = 0,
                    samplingIntervalBytes = Long.MAX_VALUE,
                    trackJavaAllocations = false,
                    tag = "bitdrift",
                ),
            )
            profiler.complete(ProfileOutcome.Failed(0, "unsupported", ""))
            result.await()
        }

    @Test
    fun aMissingArgumentFailsBeforeTheProfilerIsAsked(): Unit =
        runBlocking {
            for (name in REQUIRED_ARGUMENTS.keys) {
                val thrown = runCatching { command.execute(invocationOf(REQUIRED_ARGUMENTS - name)) }.exceptionOrNull()

                assertThat(thrown).isInstanceOf(MissingCommandArgumentException::class.java)
                assertThat((thrown as MissingCommandArgumentException).name).isEqualTo(name)
            }
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun aMistypedArgumentFailsBeforeTheProfilerIsAsked(): Unit =
        runBlocking {
            for (name in REQUIRED_ARGUMENTS.keys) {
                val thrown = runCatching { command.execute(invocation(name to CommandArgument.Text("lots"))) }.exceptionOrNull()

                assertThat(thrown).isInstanceOf(CommandArgumentTypeException::class.java)
                assertThat(thrown).hasMessageContaining(name)
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

    private companion object {
        val REQUIRED_ARGUMENTS: Map<String, CommandArgument> =
            mapOf(
                "duration_ms" to CommandArgument.Int64(5_000),
                "buffer_size_kb" to CommandArgument.Int64(8_192),
                "sampling_interval_bytes" to CommandArgument.Int64(65_536),
                "track_java_allocations" to CommandArgument.Bool(true),
            )
    }
}
