// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import kotlin.reflect.KClass

/**
 * The receiver of a command handler, describing the invocation being executed.
 */
sealed interface CommandScope {
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
     * rest of the handler. Use `argument<T>(name)` to read other types.
     */
    fun argument(name: String): String = typedArgument(name, String::class)

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

/**
 * Returns the argument named [name] as a [T], one of [String], [Long], [ULong], [Double], [Boolean]
 * or [ByteArray]. An integer is accepted as either [Long] or [ULong] when its value fits. When the
 * argument is missing, or cannot be read as a [T], the invocation completes with an
 * `invalid_arguments` error without running the rest of the handler.
 *
 * @throws IllegalArgumentException when [T] is not one of the supported types.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // The member takes no type arguments, so it never applies.
inline fun <reified T : Any> CommandScope.argument(name: String): T = typedArgument(name, T::class)

@PublishedApi
internal fun <T : Any> CommandScope.typedArgument(
    name: String,
    type: KClass<T>,
): T {
    val reader = ARGUMENT_READERS[type] ?: throw IllegalArgumentException("unsupported argument type ${type.simpleName}")
    val argument = arguments[name] ?: throw MissingCommandArgumentException(name)
    @Suppress("UNCHECKED_CAST")
    return reader.read(argument) as T? ?: throw CommandArgumentTypeException(name, reader.description, argument)
}

private class ArgumentReader(
    val description: String,
    val read: (CommandArgument) -> Any?,
)

private val ARGUMENT_READERS: Map<KClass<*>, ArgumentReader> =
    mapOf(
        String::class to ArgumentReader("a string") { (it as? CommandArgument.Text)?.value },
        Long::class to
            ArgumentReader("a long") {
                when (it) {
                    is CommandArgument.Int64 -> it.value
                    is CommandArgument.UInt64 -> it.value.takeIf { value -> value <= Long.MAX_VALUE.toULong() }?.toLong()
                    else -> null
                }
            },
        ULong::class to
            ArgumentReader("an unsigned long") {
                when (it) {
                    is CommandArgument.UInt64 -> it.value
                    is CommandArgument.Int64 -> it.value.takeIf { value -> value >= 0 }?.toULong()
                    else -> null
                }
            },
        Double::class to ArgumentReader("a double") { (it as? CommandArgument.Float64)?.value },
        Boolean::class to ArgumentReader("a boolean") { (it as? CommandArgument.Bool)?.value },
        ByteArray::class to ArgumentReader("binary") { (it as? CommandArgument.Binary)?.value },
    )

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
) : InvalidCommandArgumentException("argument $name is not $expected: $actual")
