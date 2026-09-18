// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CommandRegistryTest {
    @Test
    fun invoke_returnsResultFromHandler() {
        val registry = CommandRegistry()
        registry.register("flip_flag") { flag ->
            success(context = mapOf("flag" to flag))
        }

        val result = invokeAndAwait(registry, "flip_flag", "dark_mode")

        assertThat(result).isEqualTo(CommandResult.Success(context = mapOf("flag" to "dark_mode")))
    }

    @Test
    fun invoke_unknownCommand_returnsError() {
        val registry = CommandRegistry()

        val result = invokeAndAwait(registry, "does_not_exist")

        assertThat(result).isInstanceOf(CommandResult.Error::class.java)
        assertThat((result as CommandResult.Error).title).isEqualTo("Unknown command")
    }

    @Test
    fun invoke_afterUnregister_returnsUnknownCommand() {
        val registry = CommandRegistry()
        val handle = registry.register("flip_flag") { success() }

        handle.unregister()
        val result = invokeAndAwait(registry, "flip_flag")

        assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    }

    @Test
    fun unregister_cancelsInFlightInvocation() {
        val registry = CommandRegistry()
        val handlerStarted = CountDownLatch(1)
        val handle =
            registry.register("stuck") {
                handlerStarted.countDown()
                delay(30_000) // suspends until cancelled by unregister(), or the test times out.
                success()
            }

        val resultRef = AtomicReference<CommandResult>()
        val invocationDone = CountDownLatch(1)
        registry.invoke("stuck") { result ->
            resultRef.set(result)
            invocationDone.countDown()
        }

        assertThat(handlerStarted.await(1, TimeUnit.SECONDS)).isTrue()
        handle.unregister()

        assertThat(invocationDone.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(resultRef.get()).isEqualTo(CommandResult.Error(title = "Command cancelled"))
    }

    @Test
    fun register_withoutDispatcher_usesTheDefaultCommandsDispatcher() {
        val registry = CommandRegistry()
        val threadName = AtomicReference<String>()
        registry.register("on_default_dispatcher") {
            threadName.set(Thread.currentThread().name)
            success()
        }

        invokeAndAwait(registry, "on_default_dispatcher")

        // kotlinx.coroutines debug mode appends " @coroutine#N" to the thread name.
        assertThat(threadName.get()).startsWith("io.bitdrift.capture.commands")
    }

    @Test
    fun register_withCustomDispatcher_runsOnThatDispatcherInstead() {
        val customExecutor = Executors.newSingleThreadExecutor { Thread(it, "custom-command-thread") }
        try {
            val registry = CommandRegistry()
            val threadName = AtomicReference<String>()
            registry.register("on_custom_dispatcher", dispatcher = customExecutor.asCoroutineDispatcher()) {
                threadName.set(Thread.currentThread().name)
                success()
            }

            invokeAndAwait(registry, "on_custom_dispatcher")

            assertThat(threadName.get()).startsWith("custom-command-thread")
        } finally {
            customExecutor.shutdown()
        }
    }

    @Test
    fun unregisterOn_unregistersWhenScopeCompletes() {
        val registry = CommandRegistry()
        val externalJob = Job()
        val externalScope = CoroutineScope(externalJob)

        registry.register("checkout_state") { success() }.unregisterOn(externalScope)

        // A bare Job with no children completes (and runs invokeOnCompletion handlers)
        // synchronously within cancel(), so the command is unregistered by the time this returns.
        externalJob.cancel()

        val result = invokeAndAwait(registry, "checkout_state")
        assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    }

    private fun invokeAndAwait(
        registry: CommandRegistry,
        key: String,
        arg: String = "",
    ): CommandResult {
        val resultRef = AtomicReference<CommandResult>()
        val done = CountDownLatch(1)
        registry.invoke(key, arg) { result ->
            resultRef.set(result)
            done.countDown()
        }
        check(done.await(1, TimeUnit.SECONDS)) { "Command \"$key\" did not report a result in time" }
        return resultRef.get()
    }
}
