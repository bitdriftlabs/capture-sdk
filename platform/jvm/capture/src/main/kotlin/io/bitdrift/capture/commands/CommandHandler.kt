// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import java.util.concurrent.CompletableFuture

/**
 * Java-friendly alternative to the suspend-based command handler (`suspend
 * CommandScope.(String) -> CommandResult`). Internally adapted into a suspend call via
 * `CompletableFuture<T>.await()` and routed through the same registry/dispatcher/cancellation
 * machinery as the suspend overload -- see `Logger.registerCommand`.
 */
fun interface CommandHandler {
    /** Handles a single invocation of the command, resolving the returned future with the outcome. */
    fun handle(
        scope: CommandScope,
        arg: String,
    ): CompletableFuture<CommandResult>
}
