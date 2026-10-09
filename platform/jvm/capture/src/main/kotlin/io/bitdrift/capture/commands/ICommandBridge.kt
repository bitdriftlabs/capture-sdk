// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field

/**
 * Why a command failed, as understood by the Rust core. Each [wire] string selects a
 * `bd_logger::CommandError` variant in `commands.rs`; the same strings are used by the iOS bridge,
 * so the backend sees identical errors from both platforms. Rust raises `timeout` itself.
 */
internal enum class CommandErrorCode(
    val wire: String,
) {
    /** No handler is registered for the key. */
    COMMAND_UNKNOWN("command_unknown"),

    /** Another invocation of the same key is still running. */
    COMMAND_ALREADY_EXECUTING("command_already_executing"),

    /** The SDK-wide limit of invocations in flight was reached. */
    MAX_COMMAND_CONCURRENCY("max_command_concurrency"),

    /** The handler asked for an argument the invocation did not carry; the message says which. */
    INVALID_ARGUMENTS("invalid_arguments"),

    /** The handler threw, or returned a [CommandResult.Error]; the message carries the detail. */
    HANDLER_FAILED("handler_failed"),
}

internal interface ICommandBridge {
    fun registerCommand(
        loggerId: Long,
        key: String,
        dispatcher: ICommandDispatcher,
    )

    fun unregisterCommand(
        loggerId: Long,
        key: String,
    ): Boolean

    /**
     * @param errorCode a [CommandErrorCode.wire] value, or null when the command succeeded.
     * @param errorMessage the human-readable failure detail; null on success.
     * @param fields the result context (success) or the error context (failure).
     */
    fun completeCommand(
        invocationId: Long,
        errorCode: String?,
        errorMessage: String?,
        fields: Array<Field>,
        attachment: ByteArray?,
        attachmentContentType: String?,
    )
}
