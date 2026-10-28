// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.toFieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the handlers registered by the application and runs them when the Rust core dispatches
 * an invocation.
 *
 * Invocations are launched on [dispatcher]; the execution timeout lives in the Rust core. The SDK
 * core cancels invocations whose lifetime ends. A registration may also have an owner scope: when it
 * starts cancelling or completes normally, the registration is removed and its invocations are cancelled, as
 * structured concurrency would. The owner is fixed at registration, so it covers every invocation
 * of that registration. Two rules bound the work in flight, and both reject rather than queue so that
 * memory does not grow with the rate of incoming commands: a key runs one invocation at a time,
 * and at most [maxConcurrentInvocations] invocations exist across all keys.
 *
 * Registration is not atomic with respect to [attach]. The two possible races are harmless: a
 * key announced to the bridge twice is a replacement on the Rust side, and a key left announced
 * after removal is answered by [dispatch] with [CommandErrorCode.CommandUnknown].
 */
internal class CommandRegistry(
    private val bridge: ICommandBridge,
    dispatcher: CoroutineDispatcher = defaultDispatcher(),
    private val maxConcurrentInvocations: Int = MAX_CONCURRENT_INVOCATIONS,
) : ICommandDispatcher {
    private sealed interface Identity {
        data class App(
            val key: String,
        ) : Identity

        data class Builtin(
            val commandType: Int,
        ) : Identity
    }

    private class Builtin(
        val key: String,
        val artifactType: String,
        val timeoutMs: Long?,
        val dispatcher: ICommandDispatcher,
    )

    private val handlers = ConcurrentHashMap<Identity, Registration>()
    private val builtins = ConcurrentHashMap<Int, Builtin>()

    /** Keys with an invocation in flight; `add` is an atomic claim. (`newKeySet()` needs API 24.) */
    private val running: MutableSet<Identity> = Collections.newSetFromMap(ConcurrentHashMap())

    /** One permit per invocation in flight; only ever `tryAcquire`d, so nothing waits on it. */
    private val slots = Semaphore(maxConcurrentInvocations)

    private val invocations = ConcurrentHashMap<Long, RunningInvocation>()

    @Volatile
    private var loggerId: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun register(
        key: String,
        owner: CoroutineScope? = null,
        handler: suspend CommandScope.() -> CommandResult,
    ): CommandHandle {
        val registration = Registration(handler, owner?.coroutineContext?.get(Job))
        val identity = Identity.App(key)
        handlers[identity] = registration
        loggerId?.let { bridge.registerCommand(it, key, this) }
        // `remove(key, value)` compares by identity, so a handle only removes its own registration
        // and is a no-op once the key has been re-registered.
        val handle =
            CommandHandle(key) {
                if (handlers.remove(identity, registration)) loggerId?.let { bridge.unregisterCommand(it, key) }
            }
        registration.invokeOnOwnerEnd { handle.unregister() }
        return handle
    }

    fun unregister(key: String): Boolean {
        if (handlers.remove(Identity.App(key)) == null) return false
        loggerId?.let { bridge.unregisterCommand(it, key) }
        return true
    }

    fun registerBuiltin(
        commandType: Int,
        key: String,
        artifactType: String,
        timeoutMs: Long? = null,
        handler: suspend CommandScope.() -> CommandResult,
    ) {
        val identity = Identity.Builtin(commandType)
        handlers[identity] = Registration(handler, null)
        val adapter =
            object : ICommandDispatcher {
                override fun dispatch(
                    invocationId: Long,
                    key: String,
                    commandId: String?,
                    sessionId: String,
                    argumentNames: Array<String>,
                    argumentTypes: IntArray,
                    argumentValues: Array<Any?>,
                ) = dispatch(identity, invocationId, commandId, sessionId, argumentNames, argumentTypes, argumentValues)

                override fun cancel(invocationId: Long) = this@CommandRegistry.cancel(invocationId)
            }
        builtins[commandType] = Builtin(key, artifactType, timeoutMs, adapter)
    }

    fun attach(loggerId: Long): List<String> {
        this.loggerId = loggerId
        handlers.keys.filterIsInstance<Identity.App>().forEach { bridge.registerCommand(loggerId, it.key, this) }
        return builtins.mapNotNull { (commandType, definition) ->
            if (
                bridge.registerBuiltinCommand(
                    loggerId = loggerId,
                    commandType = commandType,
                    key = definition.key,
                    artifactType = definition.artifactType,
                    timeoutMs = definition.timeoutMs ?: 0,
                    dispatcher = definition.dispatcher,
                )
            ) {
                null
            } else {
                definition.key
            }
        }
    }

    override fun dispatch(
        invocationId: Long,
        key: String,
        commandId: String?,
        sessionId: String,
        argumentNames: Array<String>,
        argumentTypes: IntArray,
        argumentValues: Array<Any?>,
    ) {
        dispatch(Identity.App(key), invocationId, commandId, sessionId, argumentNames, argumentTypes, argumentValues)
    }

    private fun dispatch(
        identity: Identity,
        invocationId: Long,
        commandId: String?,
        sessionId: String,
        argumentNames: Array<String>,
        argumentTypes: IntArray,
        argumentValues: Array<Any?>,
    ) {
        val key =
            when (identity) {
                is Identity.App -> identity.key
                is Identity.Builtin -> builtins.getValue(identity.commandType).key
            }
        val registration = handlers[identity]
        if (registration == null) {
            fail(invocationId, CommandErrorCode.CommandUnknown, "no handler registered for $key")
            return
        }
        val arguments = HashMap<String, CommandArgument>(argumentNames.size)
        for (i in argumentNames.indices) {
            val argument = CommandArgument.decode(argumentTypes[i], argumentValues[i])
            if (argument == null) {
                fail(
                    invocationId,
                    CommandErrorCode.InvalidArguments,
                    "argument ${argumentNames[i]} has unsupported type ${argumentTypes[i]}",
                )
                return
            }
            arguments[argumentNames[i]] = argument
        }
        if (!running.add(identity)) {
            fail(invocationId, CommandErrorCode.CommandAlreadyExecuting, "an invocation of $key is already running")
            return
        }
        if (!slots.tryAcquire()) {
            running.remove(identity)
            fail(invocationId, CommandErrorCode.MaxCommandConcurrency, "$maxConcurrentInvocations commands are already running")
            return
        }
        val invocation = CommandInvocation(key, commandId, sessionId, arguments)
        val ownerCancelled = AtomicBoolean(false)
        var outcome: Outcome? = null
        // Publish before starting: an immediate completion must not leave a stale Job behind.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                outcome =
                    try {
                        // Calling a suspend function does not check for cancellation, so an owner that
                        // completed before the invocation started would otherwise still run the handler.
                        ensureActive()
                        val result = registration.handler(invocation)
                        try {
                            ensureActive()
                        } catch (cancelled: CancellationException) {
                            result.discardOwnedFile()
                            throw cancelled
                        }
                        Outcome.Returned(result)
                    } catch (e: InvalidCommandArgumentException) {
                        Outcome.Failed(CommandErrorCode.InvalidArguments, e.message.orEmpty())
                    } catch (e: Throwable) {
                        val message = if (ownerCancelled.get()) OWNER_SCOPE_COMPLETED else e.toString()
                        Outcome.Failed(CommandErrorCode.HandlerFailed, message)
                    }
            }
        val runningInvocation = RunningInvocation(job)
        invocations[invocationId] = runningInvocation
        val ownerLink =
            registration.invokeOnOwnerEnd {
                ownerCancelled.set(true)
                job.cancel()
            }
        job.invokeOnCompletion { cause ->
            ownerLink?.dispose()
            invocations.remove(invocationId, runningInvocation)
            // Completion also runs for a Job cancelled before its body starts, but only after a
            // non-cooperative handler really exits. Release every claim exactly once, before reporting.
            slots.release()
            running.remove(identity)
            if (runningInvocation.cancelledByCore.get()) {
                (outcome as? Outcome.Returned)?.result?.discardOwnedFile()
            } else {
                when (val result = outcome) {
                    is Outcome.Returned -> complete(invocationId, result.result)
                    is Outcome.Failed -> fail(invocationId, result.code, result.message)
                    null ->
                        fail(
                            invocationId,
                            CommandErrorCode.HandlerFailed,
                            if (ownerCancelled.get()) OWNER_SCOPE_COMPLETED else cause.toString(),
                        )
                }
            }
        }
        job.start()
    }

    override fun cancel(invocationId: Long) {
        val invocation = invocations[invocationId] ?: return
        invocation.cancelledByCore.set(true)
        invocation.job.cancel()
    }

    // Observe cancellation before the owner's other children finish. Making commands children of
    // the owner instead would prevent normal owner completion from cancelling outstanding commands.
    @OptIn(InternalCoroutinesApi::class)
    private fun Registration.invokeOnOwnerEnd(handler: () -> Unit): DisposableHandle? =
        owner?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { handler() }

    private class RunningInvocation(
        val job: Job,
    ) {
        val cancelledByCore = AtomicBoolean(false)
    }

    private class Registration(
        val handler: suspend CommandScope.() -> CommandResult,
        val owner: Job?,
    )

    private sealed interface Outcome {
        class Returned(
            val result: CommandResult,
        ) : Outcome

        class Failed(
            val code: CommandErrorCode,
            val message: String,
        ) : Outcome
    }

    private fun fail(
        invocationId: Long,
        code: CommandErrorCode,
        message: String,
        fields: Array<Field> = emptyArray(),
    ) {
        bridge.completeCommand(
            invocationId = invocationId,
            errorCode = code.wire,
            errorMessage = message,
            fields = fields,
            attachment = null,
        )
    }

    /** An error's title and description travel joined as the message; its context as fields. */
    private fun complete(
        invocationId: Long,
        result: CommandResult,
    ) {
        when (result) {
            is CommandResult.Success -> {
                var transferred = false
                try {
                    transferred =
                        bridge.completeCommand(
                            invocationId = invocationId,
                            errorCode = null,
                            errorMessage = null,
                            fields = result.context.toJniFields(),
                            attachment = result.attachment,
                        )
                } finally {
                    if (!transferred) result.discardOwnedFile()
                }
            }
            is CommandResult.Error ->
                fail(
                    invocationId,
                    result.code,
                    listOfNotNull(result.title, result.description).joinToString(": "),
                    result.context.toJniFields(),
                )
        }
    }

    private fun CommandResult.discardOwnedFile() {
        (this as? CommandResult.Success)
            ?.attachment
            ?.file
            ?.takeIf { it.isFile }
            ?.delete()
    }

    private fun Map<String, String>.toJniFields(): Array<Field> = map { (key, value) -> Field(key, value.toFieldValue()) }.toTypedArray()

    companion object {
        private const val OWNER_SCOPE_COMPLETED = "cancelled because the command's owner scope completed"

        /**
         * Both the cap on invocations in flight and the size of their thread pool, so an admitted
         * invocation always has a thread even when every handler blocks.
         */
        const val MAX_CONCURRENT_INVOCATIONS = 10

        /**
         * Handlers may block, so they run on the IO scheduler. `IO.limitedParallelism(n)` is a view
         * with its own permits: a blocked handler consumes neither one of [Dispatchers.IO]'s 64
         * permits nor a [Dispatchers.Default] CPU permit, so commands and the application's own
         * work cannot starve each other.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        internal fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_INVOCATIONS)
    }
}
