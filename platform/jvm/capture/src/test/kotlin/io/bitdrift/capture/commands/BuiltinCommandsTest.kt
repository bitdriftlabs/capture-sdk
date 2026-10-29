// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.commands.screenshot.IScreenshotCapturer
import io.bitdrift.capture.commands.screenshot.ScreenshotBuiltinCommand
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/** Pins the registration tuple of every built-in, which is the contract the backend relies on. */
class BuiltinCommandsTest {
    private val capturer =
        object : IScreenshotCapturer {
            var captures = 0

            override suspend fun capture(): CommandResult {
                captures++
                return CommandResult.Success()
            }
        }

    private val stackSampling: IBuiltinCommand =
        UnavailableBuiltinCommand(BuiltinCommandType.STACK_SAMPLING_TRACE, "stack_sampling_trace", 15_000, "not under test")

    @Test
    fun catalogListsEveryBuiltinWithItsWireContract() {
        val catalog = BuiltinCommands.make(capturer, stackSampling)
        assertThat(catalog.map { Triple(it.type, it.artifactType, it.timeoutMs) }).containsExactly(
            Triple(BuiltinCommandType.TAKE_SCREENSHOT, "screenshot", null),
            Triple(BuiltinCommandType.STACK_SAMPLING_TRACE, "stack_sampling_trace", 15_000L),
        )
    }

    @Test
    fun screenshotIgnoresAnyArguments(): Unit =
        runBlocking {
            val screenshot = ScreenshotBuiltinCommand(capturer)
            for (arguments in listOf(emptyMap(), mapOf("scale" to CommandArgument.Text("2")))) {
                assertThat(screenshot.execute(scope(arguments))).isInstanceOf(CommandResult.Success::class.java)
            }
            assertThat(capturer.captures).isEqualTo(2)
        }

    @Test
    fun catalogCarriesTheStackSamplingEntryItIsGiven() {
        assertThat(BuiltinCommands.make(capturer, stackSampling)).contains(stackSampling)
    }

    private fun scope(arguments: Map<String, CommandArgument>): CommandScope = CommandInvocation("key", "command", "session", arguments)
}
