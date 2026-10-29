// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.commands.screenshot.IScreenshotCapturer
import io.bitdrift.capture.providers.Field
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins command execution and cancellation: no per-key queue, one invocation per key at a time,
 * bounded parallelism, and one completion per invocation unless the Rust core cancelled it.
 * Built-in IDs mirror workflow_command.proto: 2 = TAKE_SCREENSHOT, 3 = STACK_SAMPLING_TRACE.
 * ID 4711 is deliberately unknown to WellKnownCommandType and represents a future built-in.
 */
class CommandRegistryTest {
    private val bridge = FakeCommandBridge()
    private val registry = CommandRegistry(bridge, Dispatchers.Default)

    @Test
    fun failedBuiltinRegistrationPreservesOtherCommands() {
        val registry = CommandRegistry(bridge, Dispatchers.Unconfined)
        registry.register("app") { success() }
        bridge.rejectedBuiltinTypes.add(BuiltinCommandType.TAKE_SCREENSHOT.wireValue)
        val catalog =
            BuiltinCommands.make(
                screenshotCapturer =
                    object : IScreenshotCapturer {
                        override suspend fun capture() = CommandResult.Success()
                    },
                stackSampling = stackSamplingStandIn(),
            )

        assertThat(BuiltinCommandsInstaller(registry, mock()).install(catalog, LOGGER_ID))
            .containsExactly(BuiltinCommandType.TAKE_SCREENSHOT.key)
        assertThat(bridge.builtinDispatchers.keys).containsExactly(BuiltinCommandType.STACK_SAMPLING_TRACE.wireValue)
        assertThat(bridge.builtinTimeouts).containsEntry(BuiltinCommandType.TAKE_SCREENSHOT.wireValue, 0L)
        assertThat(bridge.builtinTimeouts).containsEntry(BuiltinCommandType.STACK_SAMPLING_TRACE.wireValue, 15_000L)
        registry.dispatch(1, "app", null, "session")
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun builtinIdentityCannotBeShadowedOrUnregisteredByAnAppKey() {
        val registry = CommandRegistry(bridge, Dispatchers.Unconfined)
        registry.registerBuiltin(3, "stack_sampling_trace", "stack_sampling_trace") { error("not implemented") }
        val app = registry.register("stack_sampling_trace") { success(context = mapOf("app" to "true")) }
        registry.attach(LOGGER_ID)

        registry.dispatch(1, "stack_sampling_trace", null, "session")
        assertThat(bridge.awaitCompletion().field("app")).isEqualTo("true")
        app.unregister()
        assertThat(registry.unregister("stack_sampling_trace")).isFalse()
        bridge.builtinDispatchers.getValue(3).dispatch(
            2,
            "stack_sampling_trace",
            null,
            "session",
            emptyArray(),
            intArrayOf(),
            emptyArray(),
        )
        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo("not implemented")
        assertThat(completion.attachment).isNull()
        assertThat(completion.attachmentPath).isNull()
    }

    @Test
    fun builtinAndAppShareConcurrencyLimit() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        registry.registerBuiltin(2, "take_screenshot", "screenshot") { success() }
        registry.register("app") { awaitCancellation() }
        registry.attach(LOGGER_ID)
        registry.dispatch(1, "app", null, "session")
        queued.runAll()

        bridge.builtinDispatchers.getValue(2).dispatch(
            2,
            "take_screenshot",
            null,
            "session",
            emptyArray(),
            intArrayOf(),
            emptyArray(),
        )
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        registry.cancel(1)
        queued.runAll()
        bridge.builtinDispatchers.getValue(2).dispatch(
            3,
            "take_screenshot",
            null,
            "session",
            emptyArray(),
            intArrayOf(),
            emptyArray(),
        )
        queued.runAll()
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun fileAttachmentsPreserveByteApiAndTransferOnlyAPathForAppsAndBuiltins() =
        withAttachmentFile { file ->
            withAttachmentFile { builtinFile ->
                val bytes = byteArrayOf(1, 2, 3)
                assertThat(CommandAttachment(bytes, "application/octet-stream").bytes).isSameAs(bytes)
                val attachment = CommandAttachment.file(file, "application/octet-stream")
                assertThat(attachment.bytes).isEmpty()
                val registry = CommandRegistry(bridge, Dispatchers.Unconfined)
                registry.register("file") { success(attachment) }
                registry.registerBuiltin(4711, "file", "file") {
                    success(CommandAttachment.file(builtinFile, "application/octet-stream"))
                }
                registry.attach(LOGGER_ID)

                registry.dispatch(1, "file", null, "session")
                bridge.builtinDispatchers.getValue(4711).dispatch(
                    2,
                    "file",
                    null,
                    "session",
                    emptyArray(),
                    intArrayOf(),
                    emptyArray(),
                )
                for (source in listOf(file, builtinFile)) {
                    val completion = bridge.awaitCompletion()
                    assertThat(completion.attachment).isNull()
                    assertThat(completion.attachmentPath).isEqualTo(source.absolutePath)
                    assertThat(completion.attachmentContentType).isEqualTo("application/octet-stream")
                }
                assertThat(file).exists()
                assertThat(builtinFile).exists()
            }
        }

    @Test
    fun fileValidationIsDeferredToRustAndRejectedDirectoriesArePreserved() =
        withAttachmentFile { file ->
            assertThat(file.delete()).isTrue()
            bridge.acceptCompletion = false
            val registry = CommandRegistry(bridge, Dispatchers.Unconfined)
            registry.register("file") { success(CommandAttachment.file(file, "application/octet-stream")) }
            registry.dispatch(1, "file", null, "session")
            val missing = bridge.awaitCompletion()
            assertThat(missing.error).isNull()
            assertThat(missing.attachmentPath).isEqualTo(file.absolutePath)
            assertThat(file.mkdir()).isTrue()
            registry.dispatch(2, "file", null, "session")
            val directory = bridge.awaitCompletion()
            assertThat(directory.error).isNull()
            assertThat(directory.attachmentPath).isEqualTo(file.absolutePath)
            assertThat(file.isDirectory).isTrue()
        }

    @Test
    fun rejectedFileCompletionDeletesTheReturnedSource() =
        withAttachmentFile { file ->
            val queued = QueuedDispatcher()
            val registry = CommandRegistry(bridge, queued.dispatcher)
            bridge.acceptCompletion = false
            registry.register("file") { success(CommandAttachment.file(file, "application/octet-stream")) }

            registry.dispatch(1, "file", null, "session")
            queued.runAll()

            assertThat(file).doesNotExist()
            assertNoRetainedInvocations(registry)
        }

    @Test
    fun throwingFileCompletionDeletesTheReturnedSource() =
        withAttachmentFile { file ->
            val queued = QueuedDispatcher()
            val registry = CommandRegistry(bridge, queued.dispatcher)
            bridge.failNextCompletion = true
            registry.register("file") { success(CommandAttachment.file(file, "application/octet-stream")) }

            registry.dispatch(1, "file", null, "session")
            queued.runAll()

            assertThat(file).doesNotExist()
            assertNoRetainedInvocations(registry)
        }

    @Test
    fun fileReturnedAfterHandlerCatchesCancellationIsDeleted() =
        withAttachmentFile { file ->
            val queued = QueuedDispatcher()
            val registry = CommandRegistry(bridge, queued.dispatcher)
            registry.register("file") {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    success(CommandAttachment.file(file, "application/octet-stream"))
                }
            }
            registry.dispatch(1, "file", null, "session")
            queued.runAll()

            registry.cancel(1)
            queued.runAll()

            assertThat(file).doesNotExist()
            assertThat(bridge.pendingCompletions()).isZero()
        }

    private fun withAttachmentFile(block: (File) -> Unit) {
        val directory = File(".tmp").apply { mkdirs() }
        val file = File.createTempFile("command-attachment-", ".bin", directory)
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            block(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun jniCancellationContractHasAnUnmangledLongToVoidMethod() {
        val method = ICommandDispatcher::class.java.getMethod("cancel", java.lang.Long.TYPE)

        assertThat(method.returnType).isEqualTo(java.lang.Void.TYPE)
    }

    @Test
    fun coreCancellationReachesTheSuspendedHandlerAndReleasesItsClaims() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        val entered = AtomicBoolean()
        val exited = AtomicBoolean()
        registry.register("key") {
            entered.set(true)
            try {
                awaitCancellation()
            } finally {
                exited.set(true)
            }
        }
        registry.dispatch(1, "key", null, "session")
        queued.runAll()
        assertThat(entered.get()).isTrue()

        registry.cancel(1)
        queued.runAll()

        assertThat(exited.get()).isTrue()
        assertThat(bridge.pendingCompletions()).isZero()
        registry.register("key") { success() }
        registry.dispatch(2, "key", null, "session")
        queued.runAll()
        assertThat(bridge.awaitCompletion().invocationId).isEqualTo(2)
        assertNoRetainedInvocations(registry)
    }

    @Test
    fun cancellationBeforeFirstStartNeverCallsHandlerAndReleasesExactlyOneSlot() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        val calls = AtomicInteger()
        registry.register("key") {
            calls.incrementAndGet()
            success()
        }
        registry.register("other") { success() }
        registry.dispatch(1, "key", null, "session")

        registry.cancel(1)
        registry.cancel(1)
        queued.runAll()

        assertThat(calls.get()).isZero()
        assertThat(bridge.pendingCompletions()).isZero()
        assertNoRetainedInvocations(registry)
        registry.dispatch(2, "key", null, "session")
        registry.dispatch(3, "other", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        queued.runAll()
        assertThat(bridge.awaitCompletion().invocationId).isEqualTo(2)
        assertThat(calls.get()).isEqualTo(1)
        registry.dispatch(4, "other", null, "session")
        queued.runAll()
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun invocationIsPublishedBeforeDispatcherCanCancelItsFirstStart() {
        val queued = ConcurrentLinkedQueue<Runnable>()
        lateinit var registry: CommandRegistry
        val dispatcher =
            Executor {
                registry.cancel(1)
                queued.add(it)
            }.asCoroutineDispatcher()
        registry = CommandRegistry(bridge, dispatcher, maxConcurrentInvocations = 1)
        val ran = AtomicBoolean()
        registry.register("key") {
            ran.set(true)
            success()
        }

        registry.dispatch(1, "key", null, "session")
        generateSequence { queued.poll() }.forEach { it.run() }

        assertThat(ran.get()).isFalse()
        assertThat(bridge.pendingCompletions()).isZero()
        assertNoRetainedInvocations(registry)
        registry.register("key") { success() }
        registry.dispatch(2, "key", null, "session")
        generateSequence { queued.poll() }.forEach { it.run() }
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun immediateCompletionDoesNotRetainJobsOrLeakClaims() {
        val registry = CommandRegistry(bridge, Dispatchers.Unconfined, maxConcurrentInvocations = 1)
        registry.register("key") { success() }

        repeat(100) {
            val id = it.toLong()
            registry.dispatch(id, "key", null, "session")
            val completion = bridge.awaitCompletion()
            assertThat(completion.invocationId).isEqualTo(id)
            assertThat(completion.error).isNull()
            registry.cancel(id)
            assertNoRetainedInvocations(registry)
        }

        assertThat(bridge.pendingCompletions()).isZero()
    }

    @Test
    fun resultReturnedByHandlerAfterCatchingCancellationIsDiscarded() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher)
        val caught = AtomicBoolean()
        registry.register("key") {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                caught.set(true)
                success(context = mapOf("late" to "true"))
            }
        }
        registry.dispatch(1, "key", null, "session")
        queued.runAll()

        registry.cancel(1)
        queued.runAll()

        assertThat(caught.get()).isTrue()
        assertThat(bridge.pendingCompletions()).isZero()
        assertNoRetainedInvocations(registry)
    }

    @Test
    fun nonCooperativeHandlerKeepsKeyAndSlotUntilRealExitAndCannotPublishLateSuccess() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        val release = CompletableDeferred<Unit>()
        val exited = AtomicBoolean()
        registry.register("key") {
            try {
                withContext(NonCancellable) {
                    release.await()
                    success(context = mapOf("late" to "true"))
                }
            } finally {
                exited.set(true)
            }
        }
        registry.register("other") { success() }
        registry.dispatch(1, "key", null, "session")
        queued.runAll()

        registry.cancel(1)
        queued.runAll()

        assertThat(exited.get()).isFalse()
        registry.dispatch(2, "key", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("command_already_executing")
        registry.dispatch(3, "other", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        release.complete(Unit)
        queued.runAll()
        assertThat(exited.get()).isTrue()
        assertThat(bridge.pendingCompletions()).isZero()
        assertNoRetainedInvocations(registry)
        registry.dispatch(4, "key", null, "session")
        queued.runAll()
        assertThat(bridge.awaitCompletion().error).isNull()
        registry.dispatch(5, "other", null, "session")
        queued.runAll()
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun cancellationOnlyAffectsItsInvocationAndUnknownIdsAreHarmless() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 2)
        val release = CompletableDeferred<Unit>()
        val firstExited = AtomicBoolean()
        val secondExited = AtomicBoolean()
        registry.register("first") {
            try {
                awaitCancellation()
            } finally {
                firstExited.set(true)
            }
        }
        registry.register("second") {
            release.await()
            secondExited.set(true)
            success()
        }
        registry.dispatch(1, "first", null, "session")
        registry.dispatch(2, "second", null, "session")
        queued.runAll()

        registry.cancel(999)
        registry.cancel(1)
        registry.cancel(1)
        queued.runAll()

        assertThat(firstExited.get()).isTrue()
        assertThat(secondExited.get()).isFalse()
        assertThat(bridge.pendingCompletions()).isZero()
        release.complete(Unit)
        queued.runAll()
        assertThat(bridge.awaitCompletion().invocationId).isEqualTo(2)
        registry.cancel(2)
        assertThat(bridge.pendingCompletions()).isZero()
        assertNoRetainedInvocations(registry)
    }

    // ---------------------------------------------------------------------------------------------
    // Reporting: where it runs and what it leaves behind
    // ---------------------------------------------------------------------------------------------

    @Test
    fun ownerCancellingAQueuedInvocationReportsFromTheDispatcherNotTheCancellingThread() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher)
        val owner = Job()
        registry.register("vm", owner = CoroutineScope(owner)) { success() }
        registry.dispatch(1, "vm", null, "session")

        owner.cancel()

        assertThat(bridge.pendingCompletions()).describedAs("reported on the owner's thread").isZero()
        queued.runAll()
        assertThat(bridge.awaitCompletion().message).isEqualTo(OWNER_SCOPE_COMPLETED)
    }

    @Test
    fun bridgeFailureWhileReportingStillReleasesKeyAndSlot() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        registry.register("key") { success() }
        bridge.failNextCompletion = true

        registry.dispatch(1, "key", null, "session")
        queued.runAll()
        registry.dispatch(2, "key", null, "session")
        queued.runAll()

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(2)
        assertThat(completion.error).isNull()
        assertNoRetainedInvocations(registry)
    }

    @Test
    fun concurrentCoreCancellationReportsEachInvocationAtMostOnceAndReleasesEveryClaim() {
        val slots = 4
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = slots)
        repeat(slots) { i ->
            registry.register("k$i") {
                yield()
                success()
            }
        }
        val cancelled = mutableSetOf<Long>()
        repeat(250) { round ->
            val ids = List(slots) { round * slots + it + 1L }
            val canceller = Thread { ids.filter { it % 2 == 0L }.forEach(registry::cancel) }
            ids.forEachIndexed { i, id -> registry.dispatch(id, "k$i", null, "session") }
            canceller.start()
            canceller.join()
            cancelled += ids.filter { it % 2 == 0L }
            awaitNoRetainedInvocations(registry)
        }

        val reported = generateSequence { bridge.pollCompletion() }.map { it.invocationId }.toList()
        assertThat(reported).doesNotHaveDuplicates()
        assertThat((1L..250L * slots).filter { it !in cancelled }).allSatisfy { assertThat(reported).contains(it) }
        assertEverySlotIsFree(registry, slots)
    }

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
        registry.dispatch(1, "a", null, "session")
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
    fun staleHandleDoesNotUnregisterReplacement() {
        registry.attach(LOGGER_ID)
        val stale = registry.register("a") { success(context = mapOf("v" to "1")) }
        registry.register("a") { success(context = mapOf("v" to "2")) }

        stale.unregister()

        // The bridge was told about "a" on both registrations (Rust replaces) and never to drop it.
        assertThat(bridge.unregisterCalls).isEqualTo(0)
        assertThat(bridge.registered).contains(LOGGER_ID to "a")
        registry.dispatch(1, "a", null, "session")
        assertThat(bridge.awaitCompletion().field("v")).isEqualTo("2")
    }

    @Test
    fun currentHandleUnregistersItsOwnRegistration() {
        registry.attach(LOGGER_ID)
        registry.register("a") { success(context = mapOf("v" to "1")) }
        val current = registry.register("a") { success(context = mapOf("v" to "2")) }

        current.unregister()

        assertThat(bridge.unregisterCalls).isEqualTo(1)
        registry.dispatch(1, "a", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
    }

    @Test
    fun unregisterByKeyRemovesWhateverIsCurrent() {
        // Unlike a handle, the explicit by-key call does not care who registered the handler.
        registry.register("a") { success(context = mapOf("v" to "1")) }
        registry.register("a") { success(context = mapOf("v" to "2")) }

        assertThat(registry.unregister("a")).isTrue()
        registry.dispatch(1, "a", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
    }

    @Test
    fun lifecycleScopedHandleOutlivedByReplacementIsHarmless() {
        // VM1 registers as the owner; VM2 replaces the handler; VM1 is cleared.
        val vm1 = Job()
        registry.register("ui_state", owner = CoroutineScope(vm1)) { success(context = mapOf("vm" to "1")) }
        registry.register("ui_state") { success(context = mapOf("vm" to "2")) }

        vm1.cancel()
        registry.dispatch(1, "ui_state", null, "session")

        assertThat(bridge.awaitCompletion().field("vm")).isEqualTo("2")
    }

    @Test
    fun handleUnregisterIsIdempotent() {
        registry.attach(LOGGER_ID)
        val handle = registry.register("a") { success() }

        handle.unregister()
        handle.unregister()

        assertThat(bridge.unregisterCalls).isEqualTo(1)
        assertThat(registry.unregister("a")).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // Owner scope: a registration's owner unregisters it and cancels its in-flight invocations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun ownerCancellationDoesNotWaitForOtherChildrenToFinish() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        registry.attach(LOGGER_ID)
        val owner = Job()
        val releaseChild = CompletableDeferred<Unit>()
        CoroutineScope(owner + queued.dispatcher).launch {
            withContext(NonCancellable) { releaseChild.await() }
        }
        val cancelled = AtomicBoolean()
        registry.register("vm", owner = CoroutineScope(owner)) {
            try {
                awaitCancellation()
            } finally {
                cancelled.set(true)
            }
        }
        registry.register("other") { success() }
        registry.dispatch(1, "vm", null, "session")
        queued.runAll()

        try {
            owner.cancel()
            queued.runAll()

            assertThat(owner.isCancelled).isTrue()
            assertThat(owner.isCompleted).isFalse()
            assertThat(cancelled.get()).isTrue()
            val completion = bridge.awaitCompletion()
            assertThat(completion.invocationId).isEqualTo(1)
            assertThat(completion.message).isEqualTo(OWNER_SCOPE_COMPLETED)
            assertThat(bridge.unregisterCalls).isEqualTo(1)
            registry.dispatch(2, "vm", null, "session")
            assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")

            registry.register("late", owner = CoroutineScope(owner)) {
                error("a cancelling owner must not start a new invocation")
            }
            registry.dispatch(3, "late", null, "session")
            assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
            registry.dispatch(4, "other", null, "session")
            queued.runAll()
            assertThat(bridge.awaitCompletion().error).isNull()
            assertThat(owner.isCompleted).isFalse()
            assertNoRetainedInvocations(registry)
        } finally {
            releaseChild.complete(Unit)
            queued.runAll()
        }
        assertThat(owner.isCompleted).isTrue()
        assertThat(bridge.pendingCompletions()).isZero()
    }

    @Test
    fun normalOwnerCompletionStillCancelsOutstandingInvocations() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher)
        val owner = Job()
        val cancelled = AtomicBoolean()
        registry.register("vm", owner = CoroutineScope(owner)) {
            try {
                awaitCancellation()
            } finally {
                cancelled.set(true)
            }
        }
        registry.dispatch(1, "vm", null, "session")
        queued.runAll()

        assertThat(owner.complete()).isTrue()
        queued.runAll()

        assertThat(owner.isCompleted).isTrue()
        assertThat(cancelled.get()).isTrue()
        assertThat(bridge.awaitCompletion().message).isEqualTo(OWNER_SCOPE_COMPLETED)
        assertNoRetainedInvocations(registry)
    }

    @Test
    fun ownerCancellationKeepsNonCooperativeHandlerClaimsUntilExit() {
        val queued = QueuedDispatcher()
        val registry = CommandRegistry(bridge, queued.dispatcher, maxConcurrentInvocations = 1)
        val owner = Job()
        val release = CompletableDeferred<Unit>()
        registry.register("key", owner = CoroutineScope(owner)) {
            withContext(NonCancellable) {
                release.await()
                success(context = mapOf("late" to "true"))
            }
        }
        registry.register("other") { success() }
        registry.dispatch(1, "key", null, "session")
        queued.runAll()

        try {
            owner.cancel()
            queued.runAll()
            registry.register("key") { success() }
            registry.dispatch(2, "key", null, "session")
            assertThat(bridge.awaitCompletion().error).isEqualTo("command_already_executing")
            registry.dispatch(3, "other", null, "session")
            assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
            assertThat(bridge.pendingCompletions()).isZero()
        } finally {
            release.complete(Unit)
            queued.runAll()
        }

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(1)
        assertThat(completion.message).isEqualTo(OWNER_SCOPE_COMPLETED)
        assertThat(completion.fields).isEmpty()
        registry.dispatch(4, "key", null, "session")
        queued.runAll()
        assertThat(bridge.awaitCompletion().error).isNull()
        assertNoRetainedInvocations(registry)
        assertThat(bridge.pendingCompletions()).isZero()
    }

    @Test
    fun ownerScopeCompletionCancelsInFlightInvocation() {
        val owner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.register("vm", owner = CoroutineScope(owner)) {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.dispatch(1, "vm", null, "session")
        gate.awaitArrivals()

        owner.cancel()

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(1)
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo(OWNER_SCOPE_COMPLETED)
    }

    @Test
    fun invocationDispatchedRightAfterRegistrationIsOwned() {
        // The owner is part of the registration, so no dispatch can start before it is known.
        val owner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.attach(LOGGER_ID)
        registry.register("vm", owner = CoroutineScope(owner)) {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.dispatch(1, "vm", null, "session")
        gate.awaitArrivals()

        owner.cancel()

        assertThat(bridge.awaitCompletion().message).isEqualTo(OWNER_SCOPE_COMPLETED)
    }

    @Test
    fun ownerScopeCompletionUnregistersTheCommand() {
        val owner = Job()
        registry.register("vm", owner = CoroutineScope(owner)) { success() }

        owner.cancel()
        registry.dispatch(1, "vm", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
    }

    @Test
    fun alreadyCompletedOwnerUnregistersImmediately() {
        val owner = Job().apply { cancel() }

        registry.register("vm", owner = CoroutineScope(owner)) { success() }
        registry.dispatch(1, "vm", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
    }

    @Test
    fun ownerCancellationReleasesKeyAndSlot() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 1)
        val owner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.register("vm", owner = CoroutineScope(owner)) {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("other") { success() }
        registry.dispatch(1, "vm", null, "session")
        gate.awaitArrivals()

        owner.cancel()
        bridge.awaitCompletion()
        registry.dispatch(2, "other", null, "session")

        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun ownerScopeOnlyCancelsItsOwnRegistration() {
        val owner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.register("vm", owner = CoroutineScope(owner)) { success() }
        registry.register("unowned") {
            gate.arriveAndAwaitRelease()
            success(context = mapOf("finished" to "true"))
        }
        registry.dispatch(1, "unowned", null, "session")
        gate.awaitArrivals()

        owner.cancel()
        gate.release()

        assertThat(bridge.awaitCompletion().field("finished")).isEqualTo("true")
    }

    @Test
    fun staleOwnerDoesNotCancelReplacementInvocations() {
        val staleOwner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.register("key", owner = CoroutineScope(staleOwner)) { success() }
        registry.register("key") {
            gate.arriveAndAwaitRelease()
            success(context = mapOf("handler" to "replacement"))
        }
        registry.dispatch(1, "key", null, "session")
        gate.awaitArrivals()

        staleOwner.cancel()
        gate.release()

        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("replacement")
    }

    @Test
    fun handlerDoesNotStartOnceItsOwnerCompleted() {
        val queued = ConcurrentLinkedQueue<Runnable>()
        val registry = CommandRegistry(bridge, Executor { queued.add(it) }.asCoroutineDispatcher())
        val owner = Job()
        val handlerRan = AtomicBoolean(false)
        registry.register("vm", owner = CoroutineScope(owner)) {
            handlerRan.set(true)
            success()
        }
        registry.dispatch(1, "vm", null, "session")

        owner.cancel()
        generateSequence { queued.poll() }.forEach { it.run() }

        val completion = bridge.awaitCompletion()
        assertThat(handlerRan.get()).describedAs("handler ran after its owner completed").isFalse()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo(OWNER_SCOPE_COMPLETED)
    }

    @Test
    fun completedInvocationIsUnaffectedByLaterOwnerCancellation() {
        val owner = Job()
        registry.register("vm", owner = CoroutineScope(owner)) { success() }
        registry.dispatch(1, "vm", null, "session")
        assertThat(bridge.awaitCompletion().error).isNull()

        owner.cancel()

        Thread.sleep(100)
        assertThat(bridge.pendingCompletions()).isZero()
    }

    @Test
    fun blockingHandlerFinishingAfterOwnerCancellationIsReportedAsCancelled() {
        // Like withContext, a result produced after cancellation is discarded.
        val owner = Job()
        val gate = Gate(expectedArrivals = 1)
        registry.register("vm", owner = CoroutineScope(owner)) {
            gate.arriveAndBlockUntilReleased()
            success(context = mapOf("finished" to "true"))
        }
        registry.dispatch(1, "vm", null, "session")
        gate.awaitArrivals()

        owner.cancel()
        gate.release()

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo(OWNER_SCOPE_COMPLETED)
        assertThat(completion.fields).isEmpty()
    }

    @Test
    fun handlerSelfCancellationIsNotReportedAsOwnerCancellation() {
        registry.register("unowned") {
            currentCoroutineContext().cancel(CancellationException("gave up"))
            awaitCancellation()
        }
        registry.register("owned", owner = CoroutineScope(Job())) {
            currentCoroutineContext().cancel(CancellationException("gave up"))
            awaitCancellation()
        }

        registry.dispatch(1, "unowned", null, "session")
        registry.dispatch(2, "owned", null, "session")

        val completions = List(2) { bridge.awaitCompletion() }
        assertThat(completions.map { it.error }).containsOnly("handler_failed")
        assertThat(completions.map { it.message }).allSatisfy { assertThat(it).contains("gave up").isNotEqualTo(OWNER_SCOPE_COMPLETED) }
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
                        "args" to arguments.toSortedMap().mapValues { (it.value as CommandArgument.Text).value }.toString(),
                    ),
            )
        }

        registry.dispatch(1, "echo", "cmd-1", "session-9", "b" to text("2"), "a" to text("1"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.field("key")).isEqualTo("echo")
        assertThat(completion.field("commandId")).isEqualTo("cmd-1")
        assertThat(completion.field("sessionId")).isEqualTo("session-9")
        assertThat(completion.field("args")).isEqualTo("{a=1, b=2}")
    }

    @Test
    fun workflowInvocationsCarryNullCommandId() {
        registry.register("wf") { success(context = mapOf("hasId" to (commandId != null).toString())) }

        registry.dispatch(1, "wf", null, "session")

        assertThat(bridge.awaitCompletion().field("hasId")).isEqualTo("false")
    }

    @Test
    fun completesSuccessWithContext() {
        registry.register("flip") { success(context = mapOf("flag" to argument("flag"))) }

        registry.dispatch(7, "flip", null, "session", "flag" to text("dark_mode"))

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
        val context = mapOf("size" to "1", "filename" to "caller-name", "app_id" to "caller-app", "platform" to "caller-platform")
        registry.register("dump") {
            success(
                attachment = CommandAttachment(payload, contentType = "application/json"),
                context = context,
            )
        }

        registry.dispatch(1, "dump", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isNull()
        assertThat(completion.attachment).isEqualTo(payload)
        assertThat(completion.attachmentContentType).isEqualTo("application/json")
        assertThat(completion.fields.associate { it.key to it.stringValue }).isEqualTo(context)
    }

    @Test
    fun completesErrorWithTitleDescriptionAndContext() {
        registry.register("fail") { error("Unsupported", description = "always fails", context = mapOf("code" to "42")) }

        registry.dispatch(1, "fail", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo("Unsupported: always fails")
        assertThat(completion.fields.map { it.key to it.stringValue }).containsExactly("code" to "42")
        assertThat(completion.attachment).isNull()
    }

    @Test
    fun completesErrorWithoutDescription() {
        registry.register("fail") { error("Unsupported") }

        registry.dispatch(1, "fail", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).isEqualTo("Unsupported")
        assertThat(completion.fields).isEmpty()
    }

    @Test
    fun rejectsMissingArguments() {
        registry.register("pair") { success(context = mapOf(argument("name") to argument("value"))) }

        registry.dispatch(1, "pair", null, "session", "name" to text("only_name"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("invalid_arguments")
        assertThat(completion.message).isEqualTo("missing argument value")
    }

    @Test
    fun reportsThrowingHandlers() {
        registry.register("boom") { throw IllegalStateException("unexpected") }

        registry.dispatch(1, "boom", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).contains("IllegalStateException", "unexpected")
    }

    @Test
    fun reportsHandlerCancellationAsFailure() {
        // The registry never cancels, so a CancellationException can only come from the handler.
        registry.register("self_cancel") { throw CancellationException("gave up") }

        registry.dispatch(1, "self_cancel", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")
    }

    @Test
    fun errorCodesAreTheBridgeContract() {
        // Matched by name in commands.rs and shared with the iOS bridge.
        assertThat(CommandErrorCode.CommandUnknown.wire).isEqualTo("command_unknown")
        assertThat(CommandErrorCode.CommandAlreadyExecuting.wire).isEqualTo("command_already_executing")
        assertThat(CommandErrorCode.MaxCommandConcurrency.wire).isEqualTo("max_command_concurrency")
        assertThat(CommandErrorCode.InvalidArguments.wire).isEqualTo("invalid_arguments")
        assertThat(CommandErrorCode.HandlerFailed.wire).isEqualTo("handler_failed")
    }

    @Test
    fun appErrorsCarryTheirOwnCode() {
        registry.register("validate") { error("seconds must be positive", code = CommandErrorCode.InvalidArguments) }

        registry.dispatch(1, "validate", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("invalid_arguments")
        assertThat(completion.message).isEqualTo("seconds must be positive")
    }

    @Test
    fun appErrorsDefaultToHandlerFailed() {
        registry.register("plain") { error("nope") }

        registry.dispatch(1, "plain", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")
    }

    @Test
    fun sdkRaisedFailuresCarryNoFieldsAndAppFailuresKeepTheirContext() {
        registry.register("app") { error("Unsupported", context = mapOf("code" to "42")) }
        registry.dispatch(1, "missing", null, "session")
        registry.dispatch(2, "app", null, "session")

        val byId = List(2) { bridge.awaitCompletion() }.associateBy { it.invocationId }

        assertThat(byId.getValue(1).error).isEqualTo("command_unknown")
        assertThat(byId.getValue(1).fields).isEmpty()
        assertThat(byId.getValue(2).error).isEqualTo("handler_failed")
        assertThat(byId.getValue(2).field("code")).isEqualTo("42")
    }

    @Test
    fun reportsUnregisteredCommands() {
        registry.dispatch(1, "missing", null, "session")

        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(1)
        assertThat(completion.error).isEqualTo("command_unknown")
    }

    @Test
    fun dispatchReturnsBeforeHandlerCompletes() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("slow") {
            gate.arriveAndAwaitRelease()
            success()
        }

        registry.dispatch(1, "slow", null, "session")

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

        registry.dispatch(1, "ok", null, "session")
        registry.dispatch(2, "ko", null, "session")
        registry.dispatch(3, "throws", null, "session")
        registry.dispatch(4, "missing", null, "session")

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

        registry.dispatch(1, "a", null, "session")
        registry.dispatch(2, "b", null, "session")

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
        registry.dispatch(1, "busy", null, "session")
        gate.awaitArrivals()

        registry.dispatch(2, "busy", null, "session")
        registry.dispatch(3, "busy", null, "session")

        val rejected = List(2) { bridge.awaitCompletion() }
        assertThat(rejected.map { it.invocationId }).containsExactlyInAnyOrder(2, 3)
        assertThat(rejected.map { it.error }).containsOnly("command_already_executing")
        assertThat(rejected.first().message).contains("already running")
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
        registry.dispatch(1, "busy", null, "session")
        gate.awaitArrivals()

        registry.dispatch(2, "other", null, "session")

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
            registry.dispatch(it.toLong(), "again", null, "session")
            assertThat(bridge.awaitCompletion().error).isNull()
        }
    }

    @Test
    fun releasesKeyWhenHandlerThrows() {
        registry.register("boom") { throw IllegalStateException("unexpected") }
        registry.dispatch(1, "boom", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")

        registry.dispatch(2, "boom", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("handler_failed")
    }

    @Test
    fun releasesKeyWhenHandlerReturnsError() {
        registry.register("fail") { error("nope") }
        registry.dispatch(1, "fail", null, "session")
        assertThat(bridge.awaitCompletion().message).isEqualTo("nope")

        registry.dispatch(2, "fail", null, "session")

        assertThat(bridge.awaitCompletion().message).isEqualTo("nope")
    }

    // ---------------------------------------------------------------------------------------------
    // Concurrency: the in-flight cap across keys, never a queue
    // ---------------------------------------------------------------------------------------------

    @Test
    fun rejectsDistinctKeysBeyondMaxConcurrency() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 2)
        val gate = Gate(expectedArrivals = 2)
        listOf("a", "b", "c").forEach { key ->
            registry.register(key) {
                gate.arriveAndAwaitRelease()
                success()
            }
        }
        registry.dispatch(1, "a", null, "session")
        registry.dispatch(2, "b", null, "session")
        gate.awaitArrivals()

        registry.dispatch(3, "c", null, "session")

        val rejected = bridge.awaitCompletion()
        assertThat(rejected.invocationId).isEqualTo(3)
        assertThat(rejected.error).isEqualTo("max_command_concurrency")
        assertThat(rejected.message).isEqualTo("2 commands are already running")
        assertThat(gate.arrived()).isEqualTo(2)
        gate.release()
        assertThat(List(2) { bridge.awaitCompletion() }.map { it.invocationId }).containsExactlyInAnyOrder(1, 2)
    }

    @Test
    fun maxConcurrencyRejectionIsImmediateAndNeverRunsLater() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 1)
        val gate = Gate(expectedArrivals = 1)
        val ran = AtomicInteger()
        registry.register("a") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("b") {
            ran.incrementAndGet()
            success()
        }
        registry.dispatch(1, "a", null, "session")
        gate.awaitArrivals()

        registry.dispatch(2, "b", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        gate.release()
        bridge.awaitCompletion()

        // Freeing the slot does not revive the rejected invocation; only a new dispatch runs "b".
        Thread.sleep(100)
        assertThat(ran.get()).isZero()
        assertThat(bridge.pendingCompletions()).isZero()
    }

    @Test
    fun maxConcurrencyRejectionDoesNotLeaveKeyMarkedRunning() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 1)
        val gate = Gate(expectedArrivals = 1)
        registry.register("a") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("b") { success() }
        registry.dispatch(1, "a", null, "session")
        gate.awaitArrivals()

        registry.dispatch(2, "b", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        // Still over the cap: the second attempt is again max_command_concurrency, not already-executing.
        registry.dispatch(3, "b", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")

        gate.release()
        bridge.awaitCompletion()
        registry.dispatch(4, "b", null, "session")
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun busyRejectionDoesNotConsumeASlot() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 2)
        val gate = Gate(expectedArrivals = 1)
        registry.register("a") {
            gate.arriveAndAwaitRelease()
            success()
        }
        registry.register("b") { success() }
        registry.dispatch(1, "a", null, "session")
        gate.awaitArrivals()

        registry.dispatch(2, "a", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("command_already_executing")
        registry.dispatch(3, "b", null, "session")

        // One slot is held by "a"; the same-key rejection left the other one free for "b".
        val completion = bridge.awaitCompletion()
        assertThat(completion.invocationId).isEqualTo(3)
        assertThat(completion.error).isNull()
        gate.release()
        bridge.awaitCompletion()
    }

    @Test
    fun slotIsReleasedOnSuccessErrorAndThrow() {
        val registry = CommandRegistry(bridge, Dispatchers.Default, maxConcurrentInvocations = 1)
        registry.register("ok") { success() }
        registry.register("ko") { error("nope") }
        registry.register("throws") { throw IllegalStateException() }

        // With a single slot, each of these only runs if the previous one gave its slot back.
        listOf("ok", "ko", "throws", "ok").forEachIndexed { i, key ->
            registry.dispatch(i.toLong(), key, null, "session")
            assertThat(bridge.awaitCompletion().error).isNotEqualTo("max_command_concurrency")
        }
    }

    @Test
    fun capAppliesToSuspendedHandlersToo() {
        // A suspended handler gives its thread back but keeps its slot: the cap is on invocations,
        // not threads, so memory is bounded even when handlers are cheap to park.
        val registry = CommandRegistry(bridge)
        val total = CommandRegistry.MAX_CONCURRENT_INVOCATIONS + 1
        val gate = Gate(expectedArrivals = CommandRegistry.MAX_CONCURRENT_INVOCATIONS)
        repeat(total) { i ->
            registry.register("k$i") {
                gate.arriveAndAwaitRelease()
                success()
            }
        }

        repeat(total) { i -> registry.dispatch(i.toLong(), "k$i", null, "session") }

        gate.awaitArrivals()
        val rejected = bridge.awaitCompletion()
        assertThat(rejected.invocationId).isEqualTo((total - 1).toLong())
        assertThat(rejected.error).isEqualTo("max_command_concurrency")
        assertThat(gate.arrived()).isEqualTo(CommandRegistry.MAX_CONCURRENT_INVOCATIONS)
        gate.release()
        assertThat(List(total - 1) { bridge.awaitCompletion() }.map { it.error }).containsOnlyNulls()
    }

    @Test
    fun defaultPoolHasAThreadForEveryAdmittedBlockingHandler() {
        // Pool size == cap, so admitted handlers never wait for a thread even when all of them block.
        val registry = CommandRegistry(bridge)
        val total = CommandRegistry.MAX_CONCURRENT_INVOCATIONS
        val gate = Gate(expectedArrivals = total)
        repeat(total) { i ->
            registry.register("k$i") {
                gate.arriveAndBlockUntilReleased()
                success()
            }
        }

        repeat(total) { i -> registry.dispatch(i.toLong(), "k$i", null, "session") }

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
        registry.dispatch(1, "hang", null, "session")
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
        registry.dispatch(1, "gone", null, "session")

        assertThat(bridge.awaitCompletion().error).isEqualTo("command_unknown")
    }

    @Test
    fun keyStaysBusyAcrossUnregisterAndReRegisterUntilOldInvocationFinishes() {
        val gate = Gate(expectedArrivals = 1)
        val handle =
            registry.register("key") {
                gate.arriveAndAwaitRelease()
                success(context = mapOf("handler" to "first"))
            }
        registry.dispatch(1, "key", null, "session")
        gate.awaitArrivals()

        handle.unregister()
        registry.register("key") { success(context = mapOf("handler" to "second")) }
        registry.dispatch(2, "key", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("command_already_executing")

        gate.release()
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("first")

        registry.dispatch(3, "key", null, "session")
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("second")
    }

    @Test
    fun reRegisteringKeepsInFlightInvocationOnOldHandler() {
        val gate = Gate(expectedArrivals = 1)
        registry.register("key") {
            gate.arriveAndAwaitRelease()
            success(context = mapOf("handler" to "first"))
        }
        registry.dispatch(1, "key", null, "session")
        gate.awaitArrivals()

        registry.register("key") { success(context = mapOf("handler" to "second")) }
        registry.dispatch(2, "key", null, "session")
        assertThat(bridge.awaitCompletion().error).isEqualTo("command_already_executing")

        gate.release()
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("first")

        registry.dispatch(3, "key", null, "session")
        assertThat(bridge.awaitCompletion().field("handler")).isEqualTo("second")
    }

    // ---------------------------------------------------------------------------------------------
    // Arguments keep the type the backend sent
    // ---------------------------------------------------------------------------------------------

    @Test
    fun typedArgumentsReachTheHandlerIntact() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x80.toByte())
        val seen =
            java.util.concurrent.atomic
                .AtomicReference<Map<String, CommandArgument>>()
        registry.register("typed") {
            seen.set(arguments)
            success()
        }

        registry.dispatch(
            1,
            "typed",
            null,
            "session",
            "text" to CommandArgument.Text("hello"),
            "bytes" to CommandArgument.Binary(bytes),
            "unsigned" to CommandArgument.UInt64(ULong.MAX_VALUE),
            "signed" to CommandArgument.Int64(Long.MIN_VALUE),
            "decimal" to CommandArgument.Float64(0.1),
            "flag" to CommandArgument.Bool(true),
        )

        assertThat(bridge.awaitCompletion().error).isNull()
        assertThat(seen.get()).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "text" to CommandArgument.Text("hello"),
                "bytes" to CommandArgument.Binary(bytes),
                "unsigned" to CommandArgument.UInt64(ULong.MAX_VALUE),
                "signed" to CommandArgument.Int64(Long.MIN_VALUE),
                "decimal" to CommandArgument.Float64(0.1),
                "flag" to CommandArgument.Bool(true),
            ),
        )
    }

    @Test
    fun numericAndTextArgumentsWithTheSameDigitsAreDistinct() {
        val seen =
            java.util.concurrent.atomic
                .AtomicReference<Map<String, CommandArgument>>()
        registry.register("digits") {
            seen.set(arguments)
            success()
        }

        registry.dispatch(1, "digits", null, "session", "n" to CommandArgument.UInt64(0u), "s" to text("0"))

        bridge.awaitCompletion()
        assertThat(seen.get().getValue("n")).isNotEqualTo(seen.get().getValue("s"))
    }

    @Test
    fun argumentHelperRejectsNonTextValues() {
        registry.register("strict") { success(context = mapOf("v" to argument("n"))) }

        registry.dispatch(1, "strict", null, "session", "n" to CommandArgument.Int64(42))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("invalid_arguments")
        assertThat(completion.message).isEqualTo("argument n is not a string: Int64(value=42)")
    }

    @Test
    fun typedArgumentGetterReadsEachSupportedType() {
        val bytes = byteArrayOf(0xFF.toByte(), 0x00)
        val seen =
            java.util.concurrent.atomic
                .AtomicReference<List<Any>>()
        registry.register("typed") {
            seen.set(
                listOf(
                    argument<String>("text"),
                    argument<Long>("signed"),
                    argument<ULong>("unsigned"),
                    argument<Double>("decimal"),
                    argument<Boolean>("flag"),
                    argument<ByteArray>("bytes").toList(),
                ),
            )
            success()
        }

        registry.dispatch(
            1,
            "typed",
            null,
            "session",
            "text" to text("hello"),
            "signed" to CommandArgument.Int64(-5),
            "unsigned" to CommandArgument.UInt64(ULong.MAX_VALUE),
            "decimal" to CommandArgument.Float64(0.1),
            "flag" to CommandArgument.Bool(true),
            "bytes" to CommandArgument.Binary(bytes),
        )

        assertThat(bridge.awaitCompletion().error).isNull()
        assertThat(seen.get()).containsExactly("hello", -5L, ULong.MAX_VALUE, 0.1, true, bytes.toList())
    }

    @Test
    fun typedIntegerGetterAcceptsEitherSignednessWhenTheValueFits() {
        val seen =
            java.util.concurrent.atomic
                .AtomicReference<Pair<Long, ULong>>()
        registry.register("ints") {
            seen.set(argument<Long>("u") to argument<ULong>("s"))
            success()
        }

        registry.dispatch(1, "ints", null, "session", "u" to CommandArgument.UInt64(7u), "s" to CommandArgument.Int64(9))

        assertThat(bridge.awaitCompletion().error).isNull()
        assertThat(seen.get()).isEqualTo(7L to 9uL)
    }

    @Test
    fun typedIntegerGetterRejectsValuesThatDoNotFit() {
        registry.register("big") { success(context = mapOf("v" to argument<Long>("n").toString())) }
        registry.register("negative") { success(context = mapOf("v" to argument<ULong>("n").toString())) }

        registry.dispatch(1, "big", null, "session", "n" to CommandArgument.UInt64(ULong.MAX_VALUE))
        registry.dispatch(2, "negative", null, "session", "n" to CommandArgument.Int64(-1))

        val byId = List(2) { bridge.awaitCompletion() }.associateBy { it.invocationId }
        assertThat(byId.getValue(1).error).isEqualTo("invalid_arguments")
        assertThat(byId.getValue(1).message).isEqualTo("argument n is not a long: UInt64(value=18446744073709551615)")
        assertThat(byId.getValue(2).error).isEqualTo("invalid_arguments")
        assertThat(byId.getValue(2).message).isEqualTo("argument n is not an unsigned long: Int64(value=-1)")
    }

    @Test
    fun typedGetterRejectsWrongTypeAndMissingArguments() {
        registry.register("wrong") { success(context = mapOf("v" to argument<Boolean>("n").toString())) }
        registry.register("missing") { success(context = mapOf("v" to argument<Double>("n").toString())) }

        registry.dispatch(1, "wrong", null, "session", "n" to text("true"))
        registry.dispatch(2, "missing", null, "session")

        val byId = List(2) { bridge.awaitCompletion() }.associateBy { it.invocationId }
        assertThat(byId.getValue(1).error).isEqualTo("invalid_arguments")
        assertThat(byId.getValue(1).message).isEqualTo("argument n is not a boolean: Text(value=true)")
        assertThat(byId.getValue(2).error).isEqualTo("invalid_arguments")
        assertThat(byId.getValue(2).message).isEqualTo("missing argument n")
    }

    @Test
    fun typedGetterForAnUnsupportedTypeIsAHandlerBug() {
        registry.register("int") { success(context = mapOf("v" to argument<Int>("n").toString())) }

        registry.dispatch(1, "int", null, "session", "n" to CommandArgument.Int64(1))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("handler_failed")
        assertThat(completion.message).contains("unsupported argument type Int")
    }

    @Test
    fun unknownArgumentTypeCodeFailsBeforeTheHandlerRuns() {
        val ran = AtomicInteger()
        registry.register("x") {
            ran.incrementAndGet()
            success()
        }

        registry.dispatch(1, "x", null, "session", arrayOf("a"), intArrayOf(99), arrayOf<Any?>("value"))

        val completion = bridge.awaitCompletion()
        assertThat(completion.error).isEqualTo("invalid_arguments")
        assertThat(completion.message).isEqualTo("argument a has unsupported type 99")
        assertThat(ran.get()).isZero()
        // The key must not be left marked as running by the early failure.
        registry.dispatch(2, "x", null, "session")
        assertThat(bridge.awaitCompletion().error).isNull()
    }

    @Test
    fun valueClassMismatchFailsBeforeTheHandlerRuns() {
        registry.register("x") { success() }

        registry.dispatch(1, "x", null, "session", arrayOf("a"), intArrayOf(CommandArgument.TYPE_BOOL), arrayOf<Any?>("true"))

        assertThat(bridge.awaitCompletion().error).isEqualTo("invalid_arguments")
    }

    private fun text(value: String) = CommandArgument.Text(value)

    /** Dispatches with typed arguments, marshalled the way the JNI layer does it. */
    private fun CommandRegistry.dispatch(
        invocationId: Long,
        key: String,
        commandId: String?,
        sessionId: String,
        vararg arguments: Pair<String, CommandArgument>,
    ) {
        val values: Array<Any?> =
            arguments
                .map { (_, argument) ->
                    when (argument) {
                        is CommandArgument.Text -> argument.value
                        is CommandArgument.Binary -> argument.value
                        is CommandArgument.UInt64 -> argument.value.toLong()
                        is CommandArgument.Int64 -> argument.value
                        is CommandArgument.Float64 -> argument.value
                        is CommandArgument.Bool -> argument.value
                    }
                }.toTypedArray()
        val types =
            arguments
                .map { (_, argument) ->
                    when (argument) {
                        is CommandArgument.Text -> CommandArgument.TYPE_TEXT
                        is CommandArgument.Binary -> CommandArgument.TYPE_BINARY
                        is CommandArgument.UInt64 -> CommandArgument.TYPE_UINT64
                        is CommandArgument.Int64 -> CommandArgument.TYPE_INT64
                        is CommandArgument.Float64 -> CommandArgument.TYPE_FLOAT64
                        is CommandArgument.Bool -> CommandArgument.TYPE_BOOL
                    }
                }.toIntArray()
        dispatch(invocationId, key, commandId, sessionId, arguments.map { it.first }.toTypedArray(), types, values)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private class QueuedDispatcher {
        private val queued = ConcurrentLinkedQueue<Runnable>()
        val dispatcher = Executor { queued.add(it) }.asCoroutineDispatcher()

        fun runAll() {
            generateSequence { queued.poll() }.forEach { it.run() }
        }
    }

    private fun awaitNoRetainedInvocations(registry: CommandRegistry) {
        val field = CommandRegistry::class.java.getDeclaredField("invocations").apply { isAccessible = true }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while ((field.get(registry) as Map<*, *>).isNotEmpty()) {
            check(System.nanoTime() < deadline) { "invocations still running" }
            Thread.sleep(1)
        }
    }

    /** Exactly [slots] blocked invocations are admitted: fewer means a leaked slot, more a double release. */
    private fun assertEverySlotIsFree(
        registry: CommandRegistry,
        slots: Int,
    ) {
        val release = CompletableDeferred<Unit>()
        repeat(slots + 1) { i -> registry.register("probe$i") { release.await().let { success() } } }
        repeat(slots + 1) { i -> registry.dispatch(10_000L + i, "probe$i", null, "session") }
        assertThat(bridge.awaitCompletion().error).isEqualTo("max_command_concurrency")
        release.complete(Unit)
        assertThat(List(slots) { bridge.awaitCompletion().error }).containsOnlyNulls()
    }

    private fun assertNoRetainedInvocations(registry: CommandRegistry) {
        val field = CommandRegistry::class.java.getDeclaredField("invocations").apply { isAccessible = true }
        assertThat(field.get(registry) as Map<*, *>).isEmpty()
    }

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

    private fun stackSamplingStandIn(): IBuiltinCommand =
        UnavailableBuiltinCommand(BuiltinCommandType.STACK_SAMPLING_TRACE, "stack_sampling_trace", 15_000, "not under test")

    private class FakeCommandBridge : ICommandBridge {
        val builtinDispatchers = mutableMapOf<Int, ICommandDispatcher>()
        val rejectedBuiltinTypes = mutableSetOf<Int>()
        val builtinTimeouts = mutableMapOf<Int, Long>()

        override fun registerBuiltinCommand(
            loggerId: Long,
            commandType: Int,
            key: String,
            artifactType: String,
            timeoutMs: Long,
            dispatcher: ICommandDispatcher,
        ): Boolean {
            builtinTimeouts[commandType] = timeoutMs
            if (commandType in rejectedBuiltinTypes) return false
            builtinDispatchers[commandType] = dispatcher
            return true
        }

        val registered = mutableListOf<Pair<Long, String>>()
        var registerCalls = 0
        var unregisterCalls = 0

        /** Makes the next `completeCommand` throw, as a failing JNI call would. */
        @Volatile
        var failNextCompletion = false
        var acceptCompletion = true
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
            errorCode: String?,
            errorMessage: String?,
            fields: Array<Field>,
            attachment: CommandAttachment?,
        ): Boolean {
            if (failNextCompletion) {
                failNextCompletion = false
                throw IllegalStateException("completeCommand failed")
            }
            completions.add(
                Completion(
                    invocationId,
                    errorCode,
                    errorMessage,
                    fields.toList(),
                    attachment?.takeIf { it.file == null }?.bytes,
                    attachment?.file?.path,
                    attachment?.contentType,
                ),
            )
            return acceptCompletion
        }

        fun awaitCompletion(): Completion = checkNotNull(completions.poll(5, TimeUnit.SECONDS)) { "no completion" }

        fun pendingCompletions(): Int = completions.size

        fun pollCompletion(): Completion? = completions.poll()
    }

    private data class Completion(
        val invocationId: Long,
        /** The wire error code; null on success. */
        val error: String?,
        val message: String?,
        val fields: List<Field>,
        val attachment: ByteArray?,
        val attachmentPath: String?,
        val attachmentContentType: String?,
    ) {
        fun field(key: String): String? = fields.firstOrNull { it.key == key }?.stringValue
    }

    private companion object {
        const val LOGGER_ID = 1L
        const val OWNER_SCOPE_COMPLETED = "cancelled because the command's owner scope completed"
    }
}
