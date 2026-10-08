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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the handlers registered by the application and runs them when the Rust logger dispatches
 * an invocation.
 *
 * Every invocation is launched on [dispatcher] and left alone: nothing is ever cancelled and
 * timeouts are enforced by the Rust layer. The only rule between invocations is that a key runs
 * one at a time: while one is in flight, further invocations of the same key fail with `busy`
 * instead of waiting.
 *
 * Registration is not atomic with respect to [attach]: a key registered while the logger starts
 * may be announced to the bridge twice, which Rust treats as a replacement, or left announced
 * after being removed, which [dispatch] answers with `unregistered_command`.
 */
internal class CommandRegistry(
    private val bridge: ICommandBridge,
    dispatcher: CoroutineDispatcher = defaultDispatcher(),
) : ICommandDispatcher {
    private val handlers = ConcurrentHashMap<String, suspend CommandScope.() -> CommandResult>()

    /**
     * Keys with an invocation in flight. A second invocation of the same key is rejected, not
     * queued. `add` is an atomic check-and-claim because it is backed by `ConcurrentHashMap.put`.
     * (`ConcurrentHashMap.newKeySet()` would be the obvious choice but needs API 24; minSdk is 23.)
     */
    private val running: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    @Volatile
    private var loggerId: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun register(
        key: String,
        handler: suspend CommandScope.() -> CommandResult,
    ): CommandHandle {
        handlers[key] = handler
        loggerId?.let { bridge.registerCommand(it, key, this) }
        return CommandHandle(key) { unregister(key) }
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
            complete(invocationId, CommandResult.Error("unregistered_command", "no handler registered for $key"))
            return
        }
        if (!running.add(key)) {
            complete(invocationId, CommandResult.Error("busy", "an invocation of $key is already running"))
            return
        }
        val invocation = CommandInvocation(key, commandId, sessionId, argumentNames.zip(argumentValues).toMap())
        scope.launch {
            try {
                complete(invocationId, run(handler, invocation))
            } finally {
                running.remove(key)
            }
        }
    }

    private suspend fun run(
        handler: suspend CommandScope.() -> CommandResult,
        invocation: CommandInvocation,
    ): CommandResult =
        try {
            invocation.handler()
        } catch (e: MissingCommandArgumentException) {
            CommandResult.Error("invalid_arguments", "missing argument ${e.name}")
        } catch (e: Throwable) {
            CommandResult.Error("handler_failed", e.toString())
        }

    private fun complete(
        invocationId: Long,
        result: CommandResult,
    ) {
        when (result) {
            is CommandResult.Success ->
                bridge.completeCommand(
                    invocationId,
                    null,
                    result.context.toJniFields(),
                    result.attachment?.bytes,
                    result.attachment?.contentType,
                )
            is CommandResult.Error ->
                bridge.completeCommand(
                    invocationId,
                    result.title,
                    (result.description?.let { result.context + ("description" to it) } ?: result.context).toJniFields(),
                    null,
                    null,
                )
        }
    }

    private fun Map<String, String>.toJniFields(): Array<Field> = map { (key, value) -> Field(key, value.toFieldValue()) }.toTypedArray()

    companion object {
        /** The maximum number of command handlers running in parallel, across all keys. */
        const val MAX_PARALLELISM = 10

        /**
         * Handlers may block (file reads, memory dumps, profiling), so they get their own slice of
         * [Dispatchers.IO]: its threads are created on demand and do not count against the
         * application's CPU-bound [Dispatchers.Default] pool or the IO pool's shared limit.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        internal fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_PARALLELISM)
    }
}
