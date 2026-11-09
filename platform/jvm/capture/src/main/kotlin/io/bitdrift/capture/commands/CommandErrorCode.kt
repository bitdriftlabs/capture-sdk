// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * Why a command invocation failed.
 *
 * A handler may report [HandlerFailed] or [InvalidArguments]. The other codes describe the SDK's
 * own bookkeeping and can only be raised by the SDK: letting a handler claim them would let it
 * trigger whatever the backend does in response (retrying, backing off, hiding the command).
 *
 * The [wire] string selects the matching `CommandError` variant in the Rust core and is shared
 * with the iOS SDK, so a failure reads the same whichever platform raised it.
 */
sealed class CommandErrorCode {
    /** The string sent to the Rust core. */
    abstract val wire: String

    /** The handler could not do what it was asked. */
    object HandlerFailed : CommandErrorCode() {
        override val wire: String = "handler_failed"
    }

    /** The invocation's arguments were missing, of the wrong type, or otherwise unusable. */
    object InvalidArguments : CommandErrorCode() {
        override val wire: String = "invalid_arguments"
    }

    internal object CommandUnknown : CommandErrorCode() {
        override val wire: String = "command_unknown"
    }

    internal object CommandAlreadyExecuting : CommandErrorCode() {
        override val wire: String = "command_already_executing"
    }

    internal object MaxCommandConcurrency : CommandErrorCode() {
        override val wire: String = "max_command_concurrency"
    }
}
