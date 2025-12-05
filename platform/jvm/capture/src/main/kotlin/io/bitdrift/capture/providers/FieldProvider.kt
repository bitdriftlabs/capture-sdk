// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import io.bitdrift.capture.EMPTY_INTERNAL_FIELDS
import io.bitdrift.capture.InternalFields
import kotlin.time.Duration
import kotlin.time.DurationUnit

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
 * Converts a [Duration] into a [FieldValue] using the specified [DurationUnit].
 */
internal fun Duration.toFieldValue(durationUnit: DurationUnit): FieldValue = this.toDouble(durationUnit).toString().toFieldValue()

/**
 * Converts a [Boolean] into a [FieldValue]
 */
internal fun Boolean.toFieldValue(): FieldValue = this.toString().toFieldValue()

/**
 * Converts a Map<String, FieldValue> into an Array<Field> for efficient JNI transfer.
 */
internal fun Map<String, String>.toFields(): InternalFields {
    if (isEmpty()) return EMPTY_INTERNAL_FIELDS
    val result = arrayOfNulls<Field>(size)
    var i = 0
    for ((key, value) in this) {
        result[i++] = Field(key, value.toFieldValue())
    }
    @Suppress("UNCHECKED_CAST")
    return result as InternalFields
}

/**
 * Creates an InternalFieldsArray from pairs directly.
 * More efficient than arrayOf() for multiple pairs as it avoids intermediate array creation.
 *
 * Example:
 * ```
 * fieldsArrayOf(
 *     APP_EXIT_SOURCE_KEY to "ApplicationExitInfo",
 *     APP_EXIT_PROCESS_NAME_KEY to processName,
 *     APP_EXIT_REASON_KEY to reason.toReasonText()
 * )
 * ```
 */
internal fun fieldsOf(vararg pairs: Pair<String, String>): InternalFields =
    Array(pairs.size) { i -> Field(pairs[i].first, pairs[i].second.toFieldValue()) }

internal fun fieldsValueOf(vararg pairs: Pair<String, FieldValue>): InternalFields =
    Array(pairs.size) { i -> Field(pairs[i].first, pairs[i].second) }

/**
 * Converts an Array<Pair<String, String>> to InternalFieldsArray.
 */
internal fun Array<Pair<String, String>>.toFields(): InternalFields =
    Array(size) { i -> Field(this[i].first, this[i].second.toFieldValue()) }

/**
 * Combines multiple InternalFieldsArray into a single array efficiently.
 * Avoids multiple intermediate array allocations from chained `+` operations.
 *
 * Example:
 * ```
 * // Instead of: array1 + array2 + array3 + array4 (3 intermediate allocations)
 * combineFields(array1, array2, array3, array4) // single allocation
 * ```
 */
internal fun combineFields(vararg arrays: InternalFields): InternalFields {
    val totalSize = arrays.sumOf { it.size }
    if (totalSize == 0) return EMPTY_INTERNAL_FIELDS

    val result = ArrayList<Field>(totalSize)
    for (array in arrays) {
        for (field in array) {
            result.add(field)
        }
    }
    return result.toTypedArray()
}
