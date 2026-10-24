// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.Capture
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pins the built-in stack sampling handler: argument defaults and clamps, fail-fast when the process
 * is not profileable, the trace moved into SDK storage and attached by path, and cleanup after a
 * cancelled capture.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalBitdriftApi::class)
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
            logger = mock<IInternalLogger>(),
        )

    private fun invocation(vararg arguments: Pair<String, CommandArgument>) =
        CommandInvocation(StackSamplingCommand.KEY, "command", "session", arguments.toMap())

    private fun int(value: Long) = CommandArgument.Int64(value)

    private fun text(value: String) = CommandArgument.Text(value)

    private fun systemTrace(): File = File(systemDirectory, "profile_2026-10-24.perfetto-stack-sample").apply { writeText("trace") }

    @Test
    fun defaultsApplyAndTheTraceIsMovedAndAttachedByPath() =
        runTest {
            val trace = systemTrace()
            val result = async { command.handler(invocation()) }
            runCurrent()

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
                    "tag" to "bitdrift",
                ),
            )
        }

    @Test
    fun argumentsAreClampedAndTheTagIsPassedThrough() =
        runTest {
            val result =
                async {
                    command.handler(
                        invocation(
                            "duration_ms" to int(60_000),
                            "frequency_hz" to int(1),
                            "buffer_size_kb" to int(100_000),
                            "tag" to text("app.checkout_1"),
                        ),
                    )
                }
            runCurrent()

            assertThat(profiler.requests.single()).isEqualTo(
                ProfileRequest(1, ProfileType.STACK_SAMPLING, 10_000, 10, 16_384, "app.checkout_1"),
            )
            profiler.complete(ProfileOutcome.Saved(systemTrace().path))
            val success = result.await() as CommandResult.Success
            assertThat(success.context["duration_ms"]).isEqualTo("10000")
            assertThat(success.context["tag"]).isEqualTo("app.checkout_1")
        }

    @Test
    fun emptyTagFallsBackToTheDefault() =
        runTest {
            val result = async { command.handler(invocation("tag" to text(""))) }
            runCurrent()
            assertThat(profiler.requests.single().tag).isEqualTo("bitdrift")
            profiler.complete(ProfileOutcome.Saved(systemTrace().path))
            result.await()
        }

    @Test
    fun invalidArgumentsFailBeforeTheProfilerIsAsked() =
        runTest {
            val invalid =
                listOf(
                    invocation("tag" to text("../escape")),
                    invocation("tag" to text("with space")),
                    invocation("tag" to text("x".repeat(65))),
                    invocation("tag" to int(7)),
                    invocation("duration_ms" to text("fast")),
                )
            for (arguments in invalid) {
                val error = command.handler(arguments) as CommandResult.Error
                assertThat(error.code).isEqualTo(CommandErrorCode.InvalidArguments)
                assertThat(error.title).isEqualTo("invalid_arguments")
            }
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun nonProfileableProcessFailsWithoutCallingTheProfiler() =
        runTest {
            profileable = false

            val error = command.handler(invocation()) as CommandResult.Error

            assertThat(error.title).isEqualTo("not_profileable")
            assertThat(error.code).isEqualTo(CommandErrorCode.HandlerFailed)
            assertThat(profiler.requests).isEmpty()
        }

    @Test
    fun profilerFailureIsReportedWithItsCodeAndName() =
        runTest {
            val result = async { command.handler(invocation()) }
            runCurrent()
            profiler.complete(ProfileOutcome.Failed(2, "FAILED_RATE_LIMIT_PROCESS", "rate limited"))

            val error = result.await() as CommandResult.Error
            assertThat(error.title).isEqualTo("FAILED_RATE_LIMIT_PROCESS")
            assertThat(error.description).isEqualTo("rate limited")
            assertThat(error.context).isEqualTo(
                mapOf("profiling_error_code" to "2", "profiling_error_name" to "FAILED_RATE_LIMIT_PROCESS"),
            )
        }

    @Test
    fun requestFailureIsReportedInsteadOfEscaping() =
        runTest {
            profiler.failure = IllegalStateException("profiling service unavailable")

            val error = command.handler(invocation()) as CommandResult.Error

            assertThat(error.title).isEqualTo("request_failed")
            assertThat(error.description).contains("profiling service unavailable")
        }

    @Test
    fun cancellationStopsTheCaptureAndDeletesALateTrace() =
        runTest {
            val trace = systemTrace()
            val job = launch { command.handler(invocation()) }
            runCurrent()
            assertThat(profiler.requests).hasSize(1)

            job.cancel()
            runCurrent()
            assertThat(profiler.cancelled).containsExactly(1L)

            // The platform still saves the trace; nobody will upload it, so it is removed at once.
            profiler.complete(ProfileOutcome.Saved(trace.path))
            assertThat(trace).doesNotExist()
            assertThat(traceDirectory.listFiles().orEmpty()).isEmpty()
        }

    @Test
    fun savedTraceThatCannotBeMovedKeepsTheSystemPath() =
        runTest {
            val result = async { command.handler(invocation()) }
            runCurrent()
            val missing = File(systemDirectory, "vanished.perfetto-stack-sample")
            profiler.complete(ProfileOutcome.Saved(missing.path))

            val success = result.await() as CommandResult.Success
            assertThat(success.attachment?.file).isEqualTo(missing)
        }

    @Test
    fun firstCaptureSweepsOldOrphansButKeepsRecentTracesAndSweepsOnlyOnce() =
        runTest {
            traceDirectory.mkdirs()
            val orphan = File(traceDirectory, "orphan.perfetto-stack-sample").apply { writeText("old") }
            orphan.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)
            val recent = File(traceDirectory, "recent.perfetto-stack-sample").apply { writeText("new") }

            val first = async { command.handler(invocation()) }
            runCurrent()
            assertThat(orphan).doesNotExist()
            assertThat(recent).exists()
            profiler.complete(ProfileOutcome.Failed(0, "cancelled", "cancelled"))
            first.await()

            // A later orphan is not swept again in this process.
            val later = File(traceDirectory, "later.perfetto-stack-sample").apply { writeText("old") }
            later.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)
            val second = async { command.handler(invocation()) }
            runCurrent()
            assertThat(later).exists()
            profiler.complete(ProfileOutcome.Failed(0, "cancelled", "cancelled"))
            second.await()
        }

    @Test
    fun reservedKeyCannotBeRegisteredOrUnregisteredByTheApp() {
        assertThatThrownBy { Capture.Logger.registerCommand(StackSamplingCommand.KEY) { success() } }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(Capture.Logger.unregisterCommand(StackSamplingCommand.KEY)).isFalse()
    }
}
