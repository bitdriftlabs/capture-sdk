// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

/**
 * A single field.
 * @param key the field key.
 * @param value the field value.
 */
data class Field(
    val key: String,
    val value: FieldValue,
) {
    /**
     * The String value of the field. The property throws if the underlying
     * field value is not of a String type.
     */
    val stringValue: String
        get() {
            return when (value) {
                is FieldValue.StringField -> value.stringValue
                is FieldValue.BinaryField -> throw UnsupportedOperationException()
            }
        }

    /**
     * The Byte Array value of the field. The property throws if the underlying
     * field value is not of a Binary type.
     */
    val byteArrayValue: ByteArray
        get() {
            return when (value) {
                is FieldValue.BinaryField -> value.byteArrayValue
                is FieldValue.StringField -> throw UnsupportedOperationException()
            }
        }

    /**
     * The type of the field value. 0 means Binary field (Byte Array), 1 means String.
     */
    val valueType: Int
        get() {
            return when (value) {
                is FieldValue.BinaryField -> 0
                is FieldValue.StringField -> 1
            }
        }
}

/**
 * A single field value, representing either a string or a binary value.
 */
sealed class FieldValue {
    /**
     * A string representation of a field value.
     * @param stringValue the underlying string representation
     */
    data class StringField(
        val stringValue: String,
    ) : FieldValue() {
        override fun toString(): String = stringValue
    }

    /**
     * A binary representation of a field value.
     * @param byteArrayValue the underlying binary representation
     */
    data class BinaryField(
        val byteArrayValue: ByteArray,
    ) : FieldValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BinaryField
            return byteArrayValue.contentEquals(other.byteArrayValue)
        }

        override fun hashCode(): Int = byteArrayValue.contentHashCode()

        override fun toString(): String = String(byteArrayValue)
    }
}

typealias Fields = Map<String, String>

/**
 * A field provider is used to provide additional fields to each log event.
 *
 * It is invoked inline during logging, and so should therefore avoid doing expensive or blocking work.
 */
fun interface FieldProvider : () -> Fields

/**
 * Converts a String into FieldValue.StringField.
 */
internal fun String.toFieldValue() =
    with(this@toFieldValue) {
        FieldValue.StringField(this)
    }

/**
 * Converts a ByteArray into FieldValue.BinaryField.
 */
internal fun ByteArray.toFieldValue() =
    with(this@toFieldValue) {
        FieldValue.BinaryField(this)
    }

/**
 * Converts a key-value pair into a Field
 */
internal fun Pair<String, String>.toField(): Pair<String, FieldValue> = Pair(this.first, this.second.toFieldValue())

/**
 * Converts a Map<String, String> into a List<Field>.
 */
internal fun Map<String, String>?.toFields(): Map<String, FieldValue> =
    this?.entries?.associate {
        it.key to it.value.toFieldValue()
    } ?: emptyMap()
