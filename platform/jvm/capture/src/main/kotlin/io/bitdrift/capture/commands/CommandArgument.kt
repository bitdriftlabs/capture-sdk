// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * A command argument, delivered to the handler with the type the backend sent it as. Mirrors the
 * iOS `CommandArgument` so the same remote command behaves identically on both platforms.
 */
sealed class CommandArgument {
    /** A UTF-8 string. */
    data class Text(
        /** The string. */
        val value: String,
    ) : CommandArgument()

    /** Raw bytes, delivered unchanged. */
    class Binary(
        /** The bytes, exactly as sent. */
        val value: ByteArray,
    ) : CommandArgument() {
        override fun equals(other: Any?): Boolean = other is Binary && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "Binary(${value.size} bytes)"
    }

    /** An unsigned 64-bit integer. */
    data class UnsignedInteger(
        /** The unsigned value. */
        val value: ULong,
    ) : CommandArgument()

    /** A signed 64-bit integer. */
    data class SignedInteger(
        /** The signed value. */
        val value: Long,
    ) : CommandArgument()

    /** A 64-bit floating point number. */
    data class Decimal(
        /** The floating point value. */
        val value: Double,
    ) : CommandArgument()

    /** A boolean. */
    data class Bool(
        /** The boolean value. */
        val value: Boolean,
    ) : CommandArgument()

    internal companion object {
        // Type codes shared with `platform/jvm/core/src/commands.rs` and, by value, with the iOS
        // bridge's `ArgumentType`. They are part of the JNI contract.
        const val TYPE_TEXT = 0
        const val TYPE_BINARY = 1
        const val TYPE_UNSIGNED_INTEGER = 2
        const val TYPE_DECIMAL = 3
        const val TYPE_SIGNED_INTEGER = 4
        const val TYPE_BOOL = 5

        /**
         * Decodes one argument as marshalled over JNI, or returns null when the type code or the
         * value's class is not one this SDK understands.
         */
        fun decode(
            type: Int,
            value: Any?,
        ): CommandArgument? =
            when (type) {
                TYPE_TEXT -> (value as? String)?.let(::Text)
                TYPE_BINARY -> (value as? ByteArray)?.let(::Binary)
                TYPE_UNSIGNED_INTEGER -> (value as? Long)?.let { UnsignedInteger(it.toULong()) }
                TYPE_DECIMAL -> (value as? Double)?.let(::Decimal)
                TYPE_SIGNED_INTEGER -> (value as? Long)?.let(::SignedInteger)
                TYPE_BOOL -> (value as? Boolean)?.let(::Bool)
                else -> null
            }
    }
}
