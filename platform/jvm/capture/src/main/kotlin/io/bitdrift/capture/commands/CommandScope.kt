// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * The receiver of a command handler, describing the invocation being executed.
 */
interface CommandScope {
    /** The key the command was registered with. */
    val key: String

    /** The ID of the remote command, or null when the invocation was triggered by a workflow. */
    val commandId: String?

    /** The session the command is executed in. */
    val sessionId: String

    /** The named arguments of the invocation. */
    val arguments: Map<String, String>

    /**
     * Returns the argument named [name]. When it is missing, the invocation completes with an
     * `invalid_arguments` [CommandResult.Error] without running the rest of the handler.
     */
    fun argument(name: String): String = arguments[name] ?: throw MissingCommandArgumentException(name)

    /** Builds a [CommandResult.Success]. */
    fun success(
        attachment: CommandAttachment? = null,
        context: Map<String, String> = emptyMap(),
    ): CommandResult = CommandResult.Success(attachment, context)

    /** Builds a [CommandResult.Error]. */
    fun error(
        title: String,
        description: String? = null,
        context: Map<String, String> = emptyMap(),
    ): CommandResult = CommandResult.Error(title, description, context)
}

internal data class CommandInvocation(
    override val key: String,
    override val commandId: String?,
    override val sessionId: String,
    override val arguments: Map<String, String>,
) : CommandScope

internal class MissingCommandArgumentException(
    val name: String,
) : IllegalArgumentException("missing argument $name")
