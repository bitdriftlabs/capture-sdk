// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandInvocation
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ScreenshotBuiltinCommandTest {
    private val capturer =
        object : IScreenshotCapturer {
            var captures = 0

            override suspend fun capture(): CommandResult {
                captures++
                return CommandResult.Success()
            }
        }
    private val screenshot = ScreenshotBuiltinCommand(capturer)

    /** The registration tuple is the contract the backend relies on. */
    @Test
    fun registersAsTheTakeScreenshotBuiltin() {
        assertThat(screenshot.type).isEqualTo(BuiltinCommandType.TAKE_SCREENSHOT)
        assertThat(screenshot.artifactType).isEqualTo("screenshot")
        assertThat(screenshot.timeoutMs).isNull()
    }

    @Test
    fun ignoresAnyArguments(): Unit =
        runBlocking {
            for (arguments in listOf(emptyMap(), mapOf("scale" to CommandArgument.Text("2")))) {
                assertThat(screenshot.execute(scope(arguments))).isInstanceOf(CommandResult.Success::class.java)
            }
            assertThat(capturer.captures).isEqualTo(2)
        }

    private fun scope(arguments: Map<String, CommandArgument>): CommandScope = CommandInvocation("key", "command", "session", arguments)
}
