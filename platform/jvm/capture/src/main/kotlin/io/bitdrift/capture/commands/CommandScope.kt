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

    /** The named arguments of the invocation, each with the type the backend sent. */
    val arguments: Map<String, CommandArgument>

    /**
     * Returns the string argument named [name]. When it is missing, or was sent with a type other
     * than string, the invocation completes with an `invalid_arguments` error without running the
     * rest of the handler. Use [arguments] to read other types.
     */
    fun argument(name: String): String =
        when (val argument = arguments[name] ?: throw MissingCommandArgumentException(name)) {
            is CommandArgument.Text -> argument.value
            else -> throw CommandArgumentTypeException(name, expected = "string", actual = argument)
        }

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
        code: CommandErrorCode = CommandErrorCode.HandlerFailed,
    ): CommandResult = CommandResult.Error(title, description, context, code)
}

internal data class CommandInvocation(
    override val key: String,
    override val commandId: String?,
    override val sessionId: String,
    override val arguments: Map<String, CommandArgument>,
) : CommandScope

/** Thrown out of a handler to fail the invocation with `invalid_arguments`. */
internal sealed class InvalidCommandArgumentException(
    message: String,
) : IllegalArgumentException(message)

internal class MissingCommandArgumentException(
    val name: String,
) : InvalidCommandArgumentException("missing argument $name")

internal class CommandArgumentTypeException(
    name: String,
    expected: String,
    actual: CommandArgument,
) : InvalidCommandArgumentException("argument $name is not a $expected: $actual")
