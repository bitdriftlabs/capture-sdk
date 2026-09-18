// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * Receiver scope available inside a command handler, so a handler can call `success(...)` /
 * `error(...)` directly instead of qualifying them with `CommandResult.`.
 */
interface CommandScope {
    /** Reports a successful outcome for the command currently being handled. */
    fun success(
        attachment: Attachment? = null,
        context: Map<String, String> = emptyMap(),
    ): CommandResult

    /** Reports a failed outcome for the command currently being handled. */
    fun error(
        title: String,
        description: String? = null,
        context: Map<String, String> = emptyMap(),
    ): CommandResult
}

internal object CommandScopeImpl : CommandScope {
    override fun success(
        attachment: Attachment?,
        context: Map<String, String>,
    ): CommandResult = CommandResult.Success(attachment, context)

    override fun error(
        title: String,
        description: String?,
        context: Map<String, String>,
    ): CommandResult = CommandResult.Error(title, description, context)
}
