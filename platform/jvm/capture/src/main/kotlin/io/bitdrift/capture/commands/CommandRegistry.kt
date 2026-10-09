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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the handlers registered by the application and runs them when the Rust core dispatches
 * an invocation.
 *
 * Invocations are launched on [dispatcher] and never cancelled; the execution timeout lives in
 * the Rust core. Two rules bound the work in flight, and both reject rather than queue so that
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
    private val handlers = ConcurrentHashMap<String, suspend CommandScope.() -> CommandResult>()

    /** Keys with an invocation in flight; `add` is an atomic claim. (`newKeySet()` needs API 24.) */
    private val running: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** One permit per invocation in flight; only ever `tryAcquire`d, so nothing waits on it. */
    private val slots = Semaphore(maxConcurrentInvocations)

    @Volatile
    private var loggerId: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun register(
        key: String,
        handler: suspend CommandScope.() -> CommandResult,
    ): CommandHandle {
        handlers[key] = handler
        loggerId?.let { bridge.registerCommand(it, key, this) }
        // `remove(key, value)` compares the lambda by identity, so a handle only removes its own
        // registration and is a no-op once the key has been re-registered.
        return CommandHandle(key) {
            if (handlers.remove(key, handler)) loggerId?.let { bridge.unregisterCommand(it, key) }
        }
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
        val handler = handlers[key]
        if (handler == null) {
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
            // Release before reporting, so an observed completion implies the key is free again.
            val outcome =
                try {
                    Outcome.Returned(invocation.handler())
                } catch (e: InvalidCommandArgumentException) {
                    Outcome.Failed(CommandErrorCode.InvalidArguments, e.message.orEmpty())
                } catch (e: Throwable) {
                    Outcome.Failed(CommandErrorCode.HandlerFailed, e.toString())
                } finally {
                    slots.release()
                    running.remove(key)
                }
            when (outcome) {
                is Outcome.Returned -> complete(invocationId, outcome.result)
                is Outcome.Failed -> fail(invocationId, outcome.code, outcome.message)
            }
        }
    }

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
        /**
         * Both the cap on invocations in flight and the size of their thread pool, so an admitted
         * invocation always has a thread even when every handler blocks.
         */
        const val MAX_CONCURRENT_INVOCATIONS = 10

        /**
         * Handlers may block, so they run on their own slice of [Dispatchers.IO], whose threads
         * are created on demand and count against neither the application's [Dispatchers.Default]
         * pool nor the IO pool's shared limit.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        internal fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_INVOCATIONS)
    }
}
