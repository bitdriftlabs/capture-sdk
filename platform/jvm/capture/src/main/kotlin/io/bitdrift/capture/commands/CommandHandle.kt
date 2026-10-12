// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * A handle to a registered command.
 */
class CommandHandle internal constructor(
    /** The key the command was registered with. */
    val key: String,
    private val onUnregister: () -> Unit,
    private val onOwner: (Job) -> Unit,
) {
    /**
     * Unregisters the handler this handle was returned for. Does nothing if that handler was
     * already unregistered or replaced, so a handle never removes a registration it does not own;
     * `Logger.unregisterCommand(key)` removes whatever is current. Running invocations are not
     * cancelled; see [unregisterOn] for that.
     */
    fun unregister() {
        onUnregister()
    }

    /**
     * Ties the command to [scope], e.g. a `viewModelScope`: once the scope completes or is
     * cancelled, the command is unregistered and its in-flight invocations are cancelled, so a
     * handler does not outlive the component that registered it. A cancelled invocation is reported
     * as failed. Cancellation is cooperative: it reaches the handler at its next suspension point,
     * so a handler doing long blocking work should check `ensureActive()` periodically. Does nothing
     * for a scope without a [Job].
     */
    fun unregisterOn(scope: CoroutineScope): CommandHandle {
        val job = scope.coroutineContext[Job] ?: return this
        onOwner(job)
        job.invokeOnCompletion { unregister() }
        return this
    }
}
