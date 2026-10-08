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
) {
    /**
     * Unregisters whatever handler is currently registered for [key], the same as calling
     * `Logger.unregisterCommand(key)`. Invocations already running are not cancelled and complete
     * normally.
     */
    fun unregister() {
        onUnregister()
    }

    /**
     * Unregisters the command once [scope] completes or is cancelled, e.g. a `viewModelScope`.
     */
    fun unregisterOn(scope: CoroutineScope): CommandHandle {
        scope.coroutineContext[Job]?.invokeOnCompletion { unregister() }
        return this
    }
}
