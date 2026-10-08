// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the command execution rules: no cancellation, no per-key queue, one invocation per key at a
 * time, different keys in parallel on a bounded pool, and exactly one completion per dispatch.
 */
class CommandRegistryTest {
    private val bridge = FakeCommandBridge()
    private val registry = CommandRegistry(bridge, Dispatchers.Default)

    // ---------------------------------------------------------------------------------------------
    // Registration and the native bridge
    // ---------------------------------------------------------------------------------------------

    @Test
    fun registrationBeforeAttachIsReplayedToBridgeWithLoggerId() {
        registry.register("a") { success() }
        registry.register("b") { success() }
        assertThat(bridge.registered).isEmpty()

        registry.attach(LOGGER_ID)

        assertThat(bridge.registered).containsExactlyInAnyOrder(LOGGER_ID to "a", LOGGER_ID to "b")
    }

    @Test
    fun registrationAfterAttachReachesBridgeImmediately() {
        registry.attach(LOGGER_ID)

        registry.register("a") { success() }

        assertThat(bridge.registered).containsExactly(LOGGER_ID to "a")
    }

    @Test
    fun reRegisteringAnnouncesKeyAgainAndRustTreatsItAsReplacement() {
        registry.attach(LOGGER_ID)

        registry.register("a") { success(context = mapOf("v" to "1")) }
        registry.register("a") { success(context = mapOf("v" to "2")) }

        assertThat(bridge.registerCalls).isEqualTo(2)
        registry.dispatch(1, "a", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().field("v")).isEqualTo("2")
    }

    @Test
    fun unregisterNotifiesBridgeOnlyWhenAttached() {
        registry.register("a") { success() }
        assertThat(registry.unregister("a")).isTrue()
        assertThat(bridge.unregisterCalls).isEqualTo(0)

        registry.attach(LOGGER_ID)
        registry.register("b") { success() }
        assertThat(registry.unregister("b")).isTrue()
        assertThat(bridge.unregisterCalls).isEqualTo(1)
        assertThat(bridge.registered).isEmpty()
    }

    @Test
    fun unregisterUnknownKeyReturnsFalseWithoutTouchingBridge() {
        registry.attach(LOGGER_ID)

        assertThat(registry.unregister("missing")).isFalse()

        assertThat(bridge.unregisterCalls).isEqualTo(0)
    }

    @Test
    fun handleUnregistersWhateverCurrentlyHoldsTheKey() {
        // A handle is just the key: a stale handle removes the replacement too.
        val stale = registry.register("a") { success(context = mapOf("v" to "1")) }
        registry.register("a") { success(context = mapOf("v" to "2")) }

        stale.unregister()
        registry.dispatch(1, "a", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("unregistered_command")
    }

    @Test
    fun handleUnregisterIsIdempotent() {
        val handle = registry.register("a") { success() }

        handle.unregister()
        handle.unregister()

        assertThat(registry.unregister("a")).isFalse()
    }

    @Test
    fun unregisterOnScopeRemovesCommandWhenScopeCompletes() {
        val job = Job()
        registry.register("a") { success() }.unregisterOn(CoroutineScope(job))
        registry.dispatch(1, "a", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().error).isNull()

        job.cancel()
        registry.dispatch(2, "a", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("unregistered_command")
    }

    // ---------------------------------------------------------------------------------------------
    // Dispatch: what the handler sees and what the bridge gets back
    // ---------------------------------------------------------------------------------------------

    @Test
    fun handlerSeesKeyCommandIdSessionIdAndArguments() {
        registry.register("echo") {
            success(
                context =
                    mapOf(
                        "key" to key,
                        "commandId" to commandId.orEmpty(),
                        "sessionId" to sessionId,
                        "args" to arguments.toSortedMap().toString(),
                    ),
            )
        }

        registry.dispatch(1, "echo", "cmd-1", "session-9", arrayOf("b", "a"), arrayOf("2", "1"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.field("key")).isEqualTo("echo")
        assertThat(completion.field("commandId")).isEqualTo("cmd-1")
        assertThat(completion.field("sessionId")).isEqualTo("session-9")
        assertThat(completion.field("args")).isEqualTo("{a=1, b=2}")
    }

    @Test
    fun workflowInvocationsCarryNullCommandId() {
        registry.register("wf") { success(context = mapOf("hasId" to (commandId != null).toString())) }

        registry.dispatch(1, "wf", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().field("hasId")).isEqualTo("false")
    }

    @Test
    fun completesSuccessWithContext() {
        registry.register("flip") { success(context = mapOf("flag" to argument("flag"))) }

        registry.dispatch(7, "flip", null, "session", arrayOf("flag"), arrayOf("dark_mode"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(7)
        assertThat(completion.error).isNull()
        assertThat(completion.fields.map { it.key to it.stringValue }).containsExactly("flag" to "dark_mode")
        assertThat(completion.attachment).isNull()
        assertThat(completion.attachmentContentType).isNull()
    }

    @Test
    fun completesSuccessWithAttachment() {
        val payload = """{"max":1}""".toByteArray()
        registry.register("dump") {
            success(attachment = CommandAttachment(payload, contentType = "application/json"), context = mapOf("size" to "1"))
        }

        registry.dispatch(1, "dump", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isNull()
        assertThat(completion.attachment).isEqualTo(payload)
        assertThat(completion.attachmentContentType).isEqualTo("application/json")
        assertThat(completion.field("size")).isEqualTo("1")
    }

    @Test
    fun completesErrorWithTitleDescriptionAndContext() {
        registry.register("fail") { error("Unsupported", description = "always fails", context = mapOf("code" to "42")) }

        registry.dispatch(1, "fail", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("Unsupported")
        assertThat(completion.fields.map { it.key to it.stringValue })
            .containsExactlyInAnyOrder("code" to "42", "description" to "always fails")
        assertThat(completion.attachment).isNull()
    }

    @Test
    fun completesErrorWithoutDescriptionField() {
        registry.register("fail") { error("Unsupported") }

        registry.dispatch(1, "fail", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("Unsupported")
        assertThat(completion.fields).isEmpty()
    }

    @Test
    fun rejectsMissingArguments() {
        registry.register("pair") { success(context = mapOf(argument("name") to argument("value"))) }

        registry.dispatch(1, "pair", null, "session", arrayOf("name"), arrayOf("only_name"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("invalid_arguments")
        assertThat(completion.field("description")).isEqualTo("missing argument value")
    }

    @Test
    fun reportsThrowingHandlers() {
        registry.register("boom") { throw IllegalStateException("unexpected") }

        registry.dispatch(1, "boom", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.field("description")).contains("IllegalStateException", "unexpected")
    }

    @Test
    fun reportsHandlerCancellationAsFailure() {
        // The registry never cancels, so a CancellationException can only come from the handler.
        registry.register("self_cancel") { throw CancellationException("gave up") }

        registry.dispatch(1, "self_cancel", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")
    }

    @Test
    fun reportsUnregisteredCommands() {
        registry.dispatch(1, "missing", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(1)
        assertThat(completion.error).isEqualTo("unregistered_command")
    }

    @Test
    fun dispatchReturnsBeforeHandlerCompletes() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("slow") {
            gate.arriveAndAwaitRelease()
            success()
        }

        registry.dispatch(1, "slow", null, "session", emptyArray(), emptyArray())

        gate.awaitArrivals()
        assertThat(bridge.pendingCompletions()).isZero()
        gate.release()
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun everyDispatchCompletesExactlyOnce() {
        registry.register("ok") { success() }
        registry.register("ko") { error("nope") }
        registry.register("throws") { throw IllegalStateException() }

        registry.dispatch(1, "ok", null, "session", emptyArray(), emptyArray())
        registry.dispatch(2, "ko", null, "session", emptyArray(), emptyArray())
        registry.dispatch(3, "throws", null, "session", emptyArray(), emptyArray())
        registry.dispatch(4, "missing", null, "session", emptyArray(), emptyArray())

        val completions = List(4) { bridge.awaitCompletion() }
        assertThat(completions.map { it.invocationId }).containsExactlyInAnyOrder(1, 2, 3, 4)
        Thread.sleep(100)
        assertThat(bridge.pendingCompletions()).isZero()
    }

    // ---------------------------------------------------------------------------------------------
    // Concurrency: different keys in parallel, same key rejected while running
    // ---------------------------------------------------------------------------------------------

    @Test
    fun runsDifferentKeysConcurrently() {
        val gate = Gate(expectedArrivals = 2)
        registry.register("a") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("b") {
            gate.arriveAndAwaitRelease()
            success()
        }

        registry.dispatch(1, "a", null, "session", emptyArray(), emptyArray())
        registry.dispatch(2, "b", null, "session", emptyArray(), emptyArray())

        // Both handlers are inside the gate at once, so neither waited for the other.
        gate.awaitArrivals()
        gate.release()
        assertThat(List(2) { bridge.awaitCompletion() }.map { it.error }).containsOnlyNulls()
    }

    @Test
    fun rejectsSameKeyWhileRunning() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("busy") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.dispatch(1, "busy", null, "session", emptyArray(), emptyArray())
        gate.awaitArrivals()

        registry.dispatch(2, "busy", null, "session", emptyArray(), emptyArray())
        registry.dispatch(3, "busy", null, "session", emptyArray(), emptyArray())

        val rejected = List(2) { bridge.awaitCompletion() }
        assertThat(rejected.map { it.invocationId }).containsExactlyInAnyOrder(2, 3)
        assertThat(rejected.map { it.error }).containsOnly("busy")
        assertThat(rejected.first().field("description")).contains("busy")
        assertThat(gate.arrived()).isEqualTo(1)

        gate.release()
        val first = bridge.awaitCompletion()
        assertThat(first.invocationId).isEqualTo(1)
        assertThat(first.error).isNull()
    }

    @Test
    fun busyRejectionDoesNotAffectOtherKeys() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("busy") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("other") { success() }
        registry.dispatch(1, "busy", null, "session", emptyArray(), emptyArray())
        gate.awaitArrivals()

        registry.dispatch(2, "other", null, "session", emptyArray(), emptyArray())

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(2)
        assertThat(completion.error).isNull()
        gate.release()
        bridge.awaitCompletion()
    }

    @Test
    fun acceptsSameKeyAgainOnceFinished() {
        registry.register("again") { success() }

        repeat(3) {
            registry.dispatch(it.toLong(), "again", null, "session", emptyArray(), emptyArray())
            assertThat(bridge.awaitCompletion().error).isNull()
        }
    }

    @Test
    fun releasesKeyWhenHandlerThrows() {
        registry.register("boom") { throw IllegalStateException("unexpected") }
        registry.dispatch(1, "boom", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")

        registry.dispatch(2, "boom", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")
    }

    @Test
    fun releasesKeyWhenHandlerReturnsError() {
        registry.register("fail") { error("nope") }
        registry.dispatch(1, "fail", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().error).isEqualTo("nope")

        registry.dispatch(2, "fail", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("nope")
    }

    @Test
    fun defaultDispatcherRunsAtMostMaxParallelismBlockingHandlersAtOnce() {
        val registry = CommandRegistry(bridge)
        val total = CommandRegistry.MAX_PARALLELISM + 1
        val gate = Gate(expectedArrivals = CommandRegistry.MAX_PARALLELISM)
        repeat(total) { i ->
            registry.register("k$i") {
                gate.arriveAndBlockUntilReleased()
                success()
            }
        }

        repeat(total) { i -> registry.dispatch(i.toLong(), "k$i", null, "session", emptyArray(), emptyArray()) }

        gate.awaitArrivals()
        Thread.sleep(200)
        // Blocking handlers hold their thread, so the extra invocation waits inside the
        // dispatcher for a free one. It is delayed, not rejected.
        assertThat(gate.arrived()).isEqualTo(CommandRegistry.MAX_PARALLELISM)
        assertThat(bridge.pendingCompletions()).isZero()
        gate.release()
        assertThat(List(total) { bridge.awaitCompletion() }.map { it.error }).containsOnlyNulls()
    }

    @Test
    fun suspendedHandlersDoNotHoldPoolThreads() {
        val registry = CommandRegistry(bridge)
        val total = CommandRegistry.MAX_PARALLELISM + 1
        val gate = Gate(expectedArrivals = total)
        repeat(total) { i ->
            registry.register("k$i") {
                gate.arriveAndAwaitRelease()
                success()
            }
        }

        repeat(total) { i -> registry.dispatch(i.toLong(), "k$i", null, "session", emptyArray(), emptyArray()) }

        // A handler that suspends gives its thread back, so more than MAX_PARALLELISM can be in flight.
        gate.awaitArrivals()
        assertThat(gate.arrived()).isEqualTo(total)
        gate.release()
        assertThat(List(total) { bridge.awaitCompletion() }.map { it.error }).containsOnlyNulls()
    }

    // ---------------------------------------------------------------------------------------------
    // Unregister and re-register while an invocation is in flight: nothing is cancelled
    // ---------------------------------------------------------------------------------------------

    @Test
    fun unregisterDoesNotCancelInFlightInvocations() {
        val gate = Gate(expectedArrivals = 1)
        val handle =
            registry.register("hang") {
                gate.arriveAndAwaitRelease()
                success(context = mapOf("finished" to "true"))
            }
        registry.dispatch(1, "hang", null, "session", emptyArray(), emptyArray())
        gate.awaitArrivals()

        handle.unregister()
        gate.release()

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isNull()
        assertThat(completion.field("finished")).isEqualTo("true")
    }

    @Test
    fun unregisterRejectsLaterInvocations() {
        val handle = registry.register("gone") { success() }

        handle.unregister()
        registry.dispatch(1, "gone", null, "session", emptyArray(), emptyArray())

        assertThat(bridge.awaitCompletion().error).isEqualTo("unregistered_command")
    }

    @Test
    fun keyStaysBusyAcrossUnregisterAndReRegisterUntilOldInvocationFinishes() {
        val gate = Gate(expectedArrivals = 1)
        val handle =
            registry.register("key") {
                gate.arriveAndAwaitRelease()
                success(context = mapOf("handler" to "first"))
            }
        registry.dispatch(1, "key", null, "session", emptyArray(), emptyArray())
        gate.awaitArrivals()

        handle.unregister()
        registry.register("key") { success(context = mapOf("handler" to "second")) }
        registry.dispatch(2, "key", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().error).isEqualTo("busy")

        gate.release()
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("first")

        registry.dispatch(3, "key", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("second")
    }

    @Test
    fun reRegisteringKeepsInFlightInvocationOnOldHandler() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("key") {
            gate.arriveAndAwaitRelease()
            success(context = mapOf("handler" to "first"))
        }
        registry.dispatch(1, "key", null, "session", emptyArray(), emptyArray())
        gate.awaitArrivals()

        registry.register("key") { success(context = mapOf("handler" to "second")) }
        registry.dispatch(2, "key", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().error).isEqualTo("busy")

        gate.release()
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("first")

        registry.dispatch(3, "key", null, "session", emptyArray(), emptyArray())
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("second")
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Lets a test hold handlers inside their body until it decides to release them. */
    private class Gate(
        expectedArrivals: Int,
    ) {
        private val arrivals = CountDownLatch(expectedArrivals)
        private val count = AtomicInteger()
        private val released = CompletableDeferred<Unit>()
        private val releasedLatch = CountDownLatch(1)

        /** Suspends until released; the coroutine gives its thread back while waiting. */
        suspend fun arriveAndAwaitRelease() {
            count.incrementAndGet()
            arrivals.countDown()
            released.await()
        }

        /** Blocks the thread until released, like a handler doing synchronous I/O. */
        fun arriveAndBlockUntilReleased() {
            count.incrementAndGet()
            arrivals.countDown()
            check(releasedLatch.await(5, TimeUnit.SECONDS)) { "gate was never released" }
        }

        fun arrived(): Int = count.get()

        fun awaitArrivals() {
            check(arrivals.await(5, TimeUnit.SECONDS)) { "handlers did not start" }
        }

        fun release() {
            released.complete(Unit)
            releasedLatch.countDown()
        }
    }

    private class FakeCommandBridge : ICommandBridge {
        val registered = mutableListOf<Pair<Long, String>>()
        var registerCalls = 0
        var unregisterCalls = 0
        private val completions = LinkedBlockingQueue<Completion>()

        override fun registerCommand(
            loggerId: Long,
            key: String,
            dispatcher: ICommandDispatcher,
        ) {
            registerCalls++
            registered.add(loggerId to key)
        }

        override fun unregisterCommand(
            loggerId: Long,
            key: String,
        ): Boolean {
            unregisterCalls++
            return registered.removeAll { it.second == key }
        }

        override fun completeCommand(
            invocationId: Long,
            error: String?,
            fields: Array<Field>,
            attachment: ByteArray?,
            attachmentContentType: String?,
        ) {
            completions.add(Completion(invocationId, error, fields.toList(), attachment, attachmentContentType))
        }

        fun awaitCompletion(): Completion = checkNotNull(completions.poll(5, TimeUnit.SECONDS)) { "no completion" }

        fun pendingCompletions(): Int = completions.size
    }

    private data class Completion(
        val invocationId: Long,
        val error: String?,
        val fields: List<Field>,
        val attachment: ByteArray?,
        val attachmentContentType: String?,
    ) {
        fun field(key: String): String? = fields.firstOrNull { it.key == key }?.stringValue
    }

    private companion object {
        const val LOGGER_ID = 1L
    }
}
