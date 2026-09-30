// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CommandRegistryTest {
    private val bridge = FakeCommandBridge()
    private val registry = CommandRegistry(bridge) { Dispatchers.Default }

    @Test
    fun registersPendingCommandsOnAttach() {
        registry.register("a", null, 1.seconds) { success() }

        registry.attach(LOGGER_ID)

        assertThat(bridge.registered).containsExactly("a")
    }

    @Test
    fun completesSuccessWithContext() {
        registry.register("flip", null, 1.seconds) {
            success(context = mapOf("flag" to argument("flag")))
        }

        registry.dispatch(1, "flip", null, "session", arrayOf("flag"), arrayOf("dark_mode"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isNull()
        assertThat(completion.fields.map { it.key to it.stringValue }).containsExactly("flag" to "dark_mode")
    }

    @Test
    fun rejectsMissingArguments() {
        registry.register("pair", null, 1.seconds) { success(context = mapOf(argument("name") to argument("value"))) }

        registry.dispatch(1, "pair", null, "session", arrayOf("name"), arrayOf("only_name"))

        assertThat(bridge.awaitCompletion().error).isEqualTo("invalid_arguments")
    }

    @Test
    fun timesOutSlowHandlers() {
        registry.register("slow", null, 50.milliseconds) { awaitCancellation() }

        registry.dispatch(1, "slow", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("timeout")
    }

    @Test
    fun unregisterCancelsInFlightInvocations() {
        val started = CompletableDeferred<Unit>()
        val handle =
            registry.register("hang", null, 10.seconds) {
                started.complete(Unit)
                awaitCancellation()
            }
        registry.dispatch(1, "hang", null, "session", emptyArray(), emptyArray())
        while (!started.isCompleted) Thread.sleep(1)

        handle.unregister()

        assertThat(bridge.awaitCompletion().error).isEqualTo("cancelled")
    }

    @Test
    fun reportsUnregisteredCommands() {
        registry.dispatch(1, "missing", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("unregistered_command")
    }

    private class FakeCommandBridge : ICommandBridge {
        val registered = mutableListOf<String>()
        private val completions = LinkedBlockingQueue<Completion>()

        override fun registerCommand(
            loggerId: Long,
            key: String,
            dispatcher: ICommandDispatcher,
        ) {
            registered.add(key)
        }

        override fun unregisterCommand(
            loggerId: Long,
            key: String,
        ): Boolean = registered.remove(key)

        override fun completeCommand(
            invocationId: Long,
            error: String?,
            fields: Array<Field>,
            attachment: ByteArray?,
            attachmentType: String?,
        ) {
            completions.add(Completion(error, fields.toList()))
        }

        fun awaitCompletion(): Completion = checkNotNull(completions.poll(5, TimeUnit.SECONDS))
    }

    private data class Completion(
        val error: String?,
        val fields: List<Field>,
    )

    private companion object {
        const val LOGGER_ID = 1L
    }
}
