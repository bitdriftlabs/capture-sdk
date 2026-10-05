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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class CommandRegistry(
    private val bridge: ICommandBridge,
    defaultDispatcher: () -> CoroutineDispatcher,
) : ICommandDispatcher {
    private val defaultDispatcher by lazy(defaultDispatcher)
    private val lock = Any()
    private val registrations = HashMap<String, Registration>()
    private var loggerId: Long? = null

    fun register(
        key: String,
        dispatcher: CoroutineDispatcher?,
        timeout: Duration,
        handler: suspend CommandScope.() -> CommandResult,
    ): CommandHandle {
        val registration = Registration(key, timeout, handler, dispatcher ?: defaultDispatcher)
        synchronized(lock) {
            registrations.put(key, registration)?.cancel()
            loggerId?.let { bridge.registerCommand(it, key, this) }
        }
        return CommandHandle(key) { unregister(registration) }
    }

    fun unregister(key: String): Boolean =
        synchronized(lock) {
            val registration = registrations.remove(key) ?: return false
            registration.cancel()
            loggerId?.let { bridge.unregisterCommand(it, key) }
            true
        }

    fun attach(loggerId: Long) {
        synchronized(lock) {
            this.loggerId = loggerId
            registrations.keys.forEach { bridge.registerCommand(loggerId, it, this) }
        }
    }

    override fun dispatch(
        invocationId: Long,
        key: String,
        commandId: String?,
        sessionId: String,
        argumentNames: Array<String>,
        argumentValues: Array<String>,
    ) {
        val registration = synchronized(lock) { registrations[key] }
        val invocation = CommandInvocation(key, commandId, sessionId, argumentNames.zip(argumentValues).toMap())
        if (registration == null) {
            complete(invocationId, CommandResult.Error("unregistered_command", "no handler registered for $key"))
            return
        }
        registration.execute(invocation) { complete(invocationId, it) }
    }

    private fun unregister(registration: Registration) {
        synchronized(lock) {
            if (registrations[registration.key] !== registration) return
            registrations.remove(registration.key)
            registration.cancel()
            loggerId?.let { bridge.unregisterCommand(it, registration.key) }
        }
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

    private class Registration(
        val key: String,
        private val timeout: Duration,
        private val handler: suspend CommandScope.() -> CommandResult,
        dispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("bitdrift.command.$key"))
        private val mutex = Mutex()

        fun execute(
            invocation: CommandInvocation,
            complete: (CommandResult) -> Unit,
        ) {
            val completed = AtomicBoolean(false)
            val completeOnce = { result: CommandResult ->
                if (completed.compareAndSet(false, true)) complete(result)
            }
            scope
                .launch { completeOnce(run(invocation)) }
                .invokeOnCompletion { cause -> completeOnce(CommandResult.Error("cancelled", cause?.message)) }
        }

        fun cancel() {
            scope.cancel("command $key was unregistered")
        }

        private suspend fun run(invocation: CommandInvocation): CommandResult =
            try {
                withTimeout(timeout) { mutex.withLock { invocation.handler() } }
            } catch (e: MissingCommandArgumentException) {
                CommandResult.Error("invalid_arguments", "missing argument ${e.name}")
            } catch (e: TimeoutCancellationException) {
                CommandResult.Error("timeout", "command did not complete within $timeout")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CommandResult.Error("handler_failed", e.toString())
            }
    }

    companion object {
        val DEFAULT_TIMEOUT = 10.seconds
    }
}
