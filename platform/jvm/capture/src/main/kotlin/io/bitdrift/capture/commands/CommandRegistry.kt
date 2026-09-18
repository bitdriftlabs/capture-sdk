// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.threading.CaptureDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Registry of custom commands.
 *
 * PROTOTYPE NOTE: [invoke] stands in for the not-yet-built Rust/JNI trigger path. In the real
 * implementation, invocation is driven by native calling into this class rather than a direct
 * Kotlin call, and the [invoke] result callback would report back into native instead of a plain
 * lambda. Everything else here (registration bookkeeping, per-registration scope ownership,
 * cancellation semantics) is the real design, not a stand-in.
 */
internal class CommandRegistry(
    private val commandDispatcher: CoroutineDispatcher =
        CaptureDispatchers.Commands.executorService.asCoroutineDispatcher(),
) {
    private class Registration(
        val handler: suspend CommandScope.(String) -> CommandResult,
        val job: CompletableJob,
        val scope: CoroutineScope,
    )

    // Registration/unregistration happen on arbitrary app threads; invocation is triggered from
    // whatever thread the (eventual) JNI bridge calls in on. A plain HashMap isn't safe here.
    private val registrations = ConcurrentHashMap<String, Registration>()

    /**
     * @param dispatcher when null, the command runs on [commandDispatcher] (the SDK's own,
     * isolated from both its background pipeline and the host app's own coroutine work). Pass one
     * explicitly to opt a specific command out of that isolation -- e.g. onto `Dispatchers.IO`, if
     * it's known to do real, possibly slow, work and shouldn't share the single default thread
     * with other commands. This is a deliberate per-command trade-off, not a default.
     */
    fun register(
        key: String,
        dispatcher: CoroutineDispatcher? = null,
        handler: suspend CommandScope.(String) -> CommandResult,
    ): CommandHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + (dispatcher ?: commandDispatcher))
        registrations[key] = Registration(handler, job, scope)
        return CommandHandle { unregister(key) }
    }

    private fun unregister(key: String) {
        registrations.remove(key)?.job?.cancel()
    }

    /**
     * Looks up [key], runs its handler with [arg], and reports the outcome to [onResult] once
     * it's available. Never blocks the calling thread.
     */
    fun invoke(
        key: String,
        arg: String = "",
        onResult: (CommandResult) -> Unit,
    ) {
        val registration = registrations[key]
        if (registration == null) {
            onResult(CommandResult.Error("Unknown command", "No command registered for \"$key\""))
            return
        }

        registration.scope
            .launch {
                val result =
                    try {
                        registration.handler.invoke(CommandScopeImpl, arg)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        CommandResult.Error(title = "Command threw", description = throwable.message)
                    }
                onResult(result)
            }.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    // unregister() fired while this invocation was suspended -- no CommandResult
                    // was ever produced, so report cancellation explicitly rather than silently
                    // dropping the invocation.
                    onResult(CommandResult.Error(title = "Command cancelled"))
                }
            }
    }

    /** Cancels every outstanding registration/invocation. Called from Logger teardown. */
    fun shutdownAll() {
        registrations.keys.toList().forEach(::unregister)
    }
}
