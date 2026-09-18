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
 * A handle to a registered command. Call [unregister] to remove it; any invocation already in
 * flight when [unregister] is called is cancelled.
 */
fun interface CommandHandle {
    /** Removes this command from the registry and cancels any invocation currently in flight. */
    fun unregister()
}

/**
 * Ties this command's lifetime to [scope]: when [scope]'s [Job] completes (e.g. a `ViewModel`'s
 * `viewModelScope` when it's cleared), the command is automatically unregistered.
 *
 * The handler still always runs on the SDK's own command dispatcher, never on [scope]'s own
 * dispatcher -- [scope] is used purely as a lifecycle signal, not as an execution context.
 */
fun CommandHandle.unregisterOn(scope: CoroutineScope): CommandHandle {
    scope.coroutineContext[Job]?.invokeOnCompletion { unregister() }
    return this
}
