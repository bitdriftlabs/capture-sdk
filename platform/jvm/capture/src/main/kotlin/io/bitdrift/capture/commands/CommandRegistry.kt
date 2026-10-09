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
 * Keeps the handlers registered by the application and runs them when the Rust logger dispatches
 * an invocation.
 *
 * Every invocation is launched on [dispatcher] and left alone: nothing is ever cancelled and
 * timeouts are enforced by the Rust layer. Two rules bound the work in flight, and both reject
 * instead of queueing so that memory never grows with the rate of incoming commands:
 * - a key runs one invocation at a time; a repeat while one is in flight fails with
 *   `command_already_executing`;
 * - at most [maxConcurrentInvocations] invocations exist across all keys; any more fail with
 *   `max_command_concurrency`.
 *
 * Registration is not atomic with respect to [attach]: a key registered while the logger starts
 * may be announced to the bridge twice, which Rust treats as a replacement, or left announced
 * after being removed, which [dispatch] answers with `command_unknown`.
 */
internal class CommandRegistry(
    private val bridge: ICommandBridge,
    dispatcher: CoroutineDispatcher = defaultDispatcher(),
    private val maxConcurrentInvocations: Int = MAX_CONCURRENT_INVOCATIONS,
) : ICommandDispatcher {
    private val handlers = ConcurrentHashMap<String, suspend CommandScope.() -> CommandResult>()

    /**
     * Keys with an invocation in flight. A second invocation of the same key is rejected, not
     * queued. `add` is an atomic check-and-claim because it is backed by `ConcurrentHashMap.put`.
     * (`ConcurrentHashMap.newKeySet()` would be the obvious choice but needs API 24; minSdk is 23.)
     */
    private val running: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** One permit per invocation in flight; `tryAcquire` never suspends, so there is no queue. */
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
        // The handle only removes its own registration: `remove(key, value)` is atomic and compares
        // lambdas by identity, so a stale handle is a no-op once the key has been re-registered.
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
        argumentValues: Array<String>,
    ) {
        val handler = handlers[key]
        if (handler == null) {
            fail(invocationId, CommandErrorCode.COMMAND_UNKNOWN, "no handler registered for $key")
            return
        }
        if (!running.add(key)) {
            fail(invocationId, CommandErrorCode.COMMAND_ALREADY_EXECUTING, "an invocation of $key is already running")
            return
        }
        if (!slots.tryAcquire()) {
            running.remove(key)
            fail(invocationId, CommandErrorCode.MAX_COMMAND_CONCURRENCY, "$maxConcurrentInvocations commands are already running")
            return
        }
        val invocation = CommandInvocation(key, commandId, sessionId, argumentNames.zip(argumentValues).toMap())
        scope.launch {
            // Free the slot and the key before reporting the result, so that by the time anyone
            // observes this invocation as finished, the key can be invoked again.
            val outcome =
                try {
                    Outcome.Returned(invocation.handler())
                } catch (e: MissingCommandArgumentException) {
                    Outcome.Failed(CommandErrorCode.INVALID_ARGUMENTS, "missing argument ${e.name}")
                } catch (e: Throwable) {
                    Outcome.Failed(CommandErrorCode.HANDLER_FAILED, e.toString())
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

    /** Reports a failure raised by the SDK itself. */
    private fun fail(
        invocationId: Long,
        code: CommandErrorCode,
        message: String,
        fields: Array<Field> = emptyArray(),
    ) {
        bridge.completeCommand(invocationId, code.wire, message, fields, null, null)
    }

    /**
     * Reports the handler's own result. An application [CommandResult.Error] is reported as
     * `handler_failed` with "title: description" as the message and its context as fields, the
     * same shape the iOS SDK produces.
     */
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
                    result.attachment?.contentType,
                )
            is CommandResult.Error ->
                fail(
                    invocationId,
                    CommandErrorCode.HANDLER_FAILED,
                    listOfNotNull(result.title, result.description).joinToString(": "),
                    result.context.toJniFields(),
                )
        }
    }

    private fun Map<String, String>.toJniFields(): Array<Field> = map { (key, value) -> Field(key, value.toFieldValue()) }.toTypedArray()

    companion object {
        /**
         * The maximum number of invocations in flight across all keys, and the size of the thread
         * pool they run on. The two match so that an admitted invocation always has a thread even
         * when every handler blocks; the dispatcher's own queue then only ever holds resumptions.
         */
        const val MAX_CONCURRENT_INVOCATIONS = 10

        /**
         * Handlers may block (file reads, memory dumps, profiling), so they get their own slice of
         * [Dispatchers.IO]: its threads are created on demand and do not count against the
         * application's CPU-bound [Dispatchers.Default] pool or the IO pool's shared limit.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        internal fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_INVOCATIONS)
    }
}
