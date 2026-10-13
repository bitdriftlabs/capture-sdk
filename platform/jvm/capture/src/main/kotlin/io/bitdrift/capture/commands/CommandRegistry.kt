// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.toFieldValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * never cancels an invocation on its own, but a registration may have an owner scope: when it
 * completes, the registration is removed and its in-flight invocations are cancelled, as
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
    private val handlers = ConcurrentHashMap<String, Registration>()

    /** Keys with an invocation in flight; `add` is an atomic claim. (`newKeySet()` needs API 24.) */
    private val running: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** One permit per invocation in flight; only ever `tryAcquire`d, so nothing waits on it. */
    private val slots = Semaphore(maxConcurrentInvocations)

    @Volatile
    private var loggerId: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun register(
        key: String,
        owner: CoroutineScope? = null,
        handler: suspend CommandScope.() -> CommandResult,
    ): CommandHandle {
        val registration = Registration(handler, owner?.coroutineContext?.get(Job))
        handlers[key] = registration
        loggerId?.let { bridge.registerCommand(it, key, this) }
        // `remove(key, value)` compares by identity, so a handle only removes its own registration
        // and is a no-op once the key has been re-registered.
        val handle =
            CommandHandle(key) {
                if (handlers.remove(key, registration)) loggerId?.let { bridge.unregisterCommand(it, key) }
            }
        registration.owner?.invokeOnCompletion { handle.unregister() }
        return handle
    }

    fun unregister(key: String): Boolean {
        if (handlers.remove(key) == null) return false
        loggerId?.let { bridge.unregisterCommand(it, key) }
        return true
    }

    fun attach(loggerId: Long) {
        this.loggerId = loggerId
        handlers.keys.forEach { bridge.registerCommand(loggerId, it, this) }
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
        val registration = handlers[key]
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
        if (!running.add(key)) {
            fail(invocationId, CommandErrorCode.CommandAlreadyExecuting, "an invocation of $key is already running")
            return
        }
        if (!slots.tryAcquire()) {
            running.remove(key)
            fail(invocationId, CommandErrorCode.MaxCommandConcurrency, "$maxConcurrentInvocations commands are already running")
            return
        }
        val invocation = CommandInvocation(key, commandId, sessionId, arguments)
        scope.launch {
            val invocationJob = coroutineContext[Job]
            val ownerCancelled = AtomicBoolean(false)
            val ownerLink =
                registration.owner?.invokeOnCompletion {
                    ownerCancelled.set(true)
                    invocationJob?.cancel()
                }
            // Release before reporting, so an observed completion implies the key is free again.
            val outcome =
                try {
                    // Calling a suspend function does not check for cancellation, so an owner that
                    // completed before the invocation started would otherwise still run the handler.
                    ensureActive()
                    val result = registration.handler(invocation)
                    // Like withContext, a result produced after cancellation is discarded.
                    ensureActive()
                    Outcome.Returned(result)
                } catch (e: InvalidCommandArgumentException) {
                    Outcome.Failed(CommandErrorCode.InvalidArguments, e.message.orEmpty())
                } catch (e: Throwable) {
                    // Every invocation must be reported, so cancellation is caught here and rethrown
                    // below once reported. Anything other than the owner's cancellation, including a
                    // handler cancelling itself, is the handler's own failure.
                    val message = if (ownerCancelled.get()) OWNER_SCOPE_COMPLETED else e.toString()
                    Outcome.Failed(CommandErrorCode.HandlerFailed, message)
                } finally {
                    ownerLink?.dispose()
                    slots.release()
                    running.remove(key)
                }
            when (outcome) {
                is Outcome.Returned -> complete(invocationId, outcome.result)
                is Outcome.Failed -> fail(invocationId, outcome.code, outcome.message)
            }
            ensureActive()
        }
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
        bridge.completeCommand(invocationId, code.wire, message, fields, null, null, null)
    }

    /** An error's title and description travel joined as the message; its context as fields. */
    private fun complete(
        invocationId: Long,
        result: CommandResult,
    ) {
        when (result) {
            is CommandResult.Success ->
                bridge.completeCommand(
                    invocationId,
                    null,
                    null,
                    result.context.toJniFields(),
                    result.attachment?.bytes,
                    result.attachment?.filename,
                    result.attachment?.contentType,
                )
            is CommandResult.Error ->
                fail(
                    invocationId,
                    result.code,
                    listOfNotNull(result.title, result.description).joinToString(": "),
                    result.context.toJniFields(),
                )
        }
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
