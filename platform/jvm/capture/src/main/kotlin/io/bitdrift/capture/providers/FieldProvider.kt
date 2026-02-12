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
 * Holds parallel arrays of field keys and values.
 * This is an optimization to avoid creating wrapper objects when passing to JNI.
 * keys[i] corresponds to values[i].
 *
 * Note to construct it please use, fieldsOf(vararg pairs: Pair<String, String>): ArrayFields
 *
 * @property keys Array of field keys
 * @property values Array of field values corresponding to keys
 */
class ArrayFields internal constructor(
    internal val keys: Array<String>,
    internal val values: Array<String>,
) {
    internal val size: Int get() = keys.size

    internal fun isEmpty(): Boolean = keys.isEmpty()

    internal fun isNotEmpty(): Boolean = keys.isNotEmpty()

    internal operator fun get(key: String): String? {
        val index = keys.indexOf(key)
        return if (index >= 0) values[index] else null
    }

    /**
     * Checks equality based on the contents of [keys] and [values] arrays.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArrayFields) return false
        return keys.contentEquals(other.keys) && values.contentEquals(other.values)
    }

    /**
     * Computes hash code based on the contents of [keys] and [values] arrays.
     */
    override fun hashCode(): Int = 31 * keys.contentHashCode() + values.contentHashCode()

    /**
     * Companion object providing constants for [ArrayFields].
     */
    companion object {
        /**
         * Provides an empty ArrayFields instance.
         */
        val EMPTY = ArrayFields(emptyArray(), emptyArray())
    }
}

/**
 * Creates an Array<Field> with FieldValue entries (for JNI calls that need binary data).
 */
internal fun jniFieldsOf(vararg pairs: Pair<String, FieldValue>): Array<Field> =
    Array(pairs.size) { i -> Field(pairs[i].first, pairs[i].second) }

/**
 * Combines Fields with Array<Field> into a single Array<Field>.
 * Used for cases where we need to mix string fields with binary fields.
 */
internal fun combineJniFields(
    stringArrayFields: ArrayFields,
    binaryFields: Array<Field>,
): Array<Field> {
    val totalSize = stringArrayFields.size + binaryFields.size
    if (totalSize == 0) return emptyArray()

    val result = ArrayList<Field>(totalSize)
    for (i in 0 until stringArrayFields.size) {
        result.add(Field(stringArrayFields.keys[i], FieldValue.StringField(stringArrayFields.values[i])))
    }
    result.addAll(binaryFields)
    return result.toTypedArray()
}

/**
 * Creates a [ArrayFields] instance containing a single key-value pair.
 *
 * This is an optimized alternative to `fieldsOf("key" to "value")` that avoids
 * creating a Pair object and useful where there is only one entry to be added.
 *
 * @param key The field key.
 * @param value The field value.
 * @return A [ArrayFields] instance containing the single key-value pair.
 *
 * Example:
 * ```
 * fieldOf("user_id", "12345")
 * ```
 */
fun fieldOf(
    key: String,
    value: String,
): ArrayFields = ArrayFields(arrayOf(key), arrayOf(value))

/**
 * Creates a [ArrayFields] instance from key-value pairs.
 *
 * @param pairs Vararg of key-value pairs, where each pair is created using `"key" to "value"` syntax.
 * @return A [ArrayFields] instance containing the provided key-value pairs, or [ArrayFields.EMPTY] if no pairs provided.
 *
 * Example:
 * ```
 * fieldsOf(
 *     "key_1" to "value_1",
 *     "key_2" to "value_2"
 * )
 * ```
 */
fun fieldsOf(vararg pairs: Pair<String, String>): ArrayFields {
    if (pairs.isEmpty()) return ArrayFields.EMPTY
    val keys = Array(pairs.size) { pairs[it].first }
    val values = Array(pairs.size) { pairs[it].second }
    return ArrayFields(keys, values)
}

/**
 * Creates a [ArrayFields] instance from key-value pairs, filtering out pairs with null values.
 *
 * @param pairs Vararg of key-value pairs, where each pair is created using `"key" to value` syntax.
 *              Pairs with null values are excluded from the result.
 * @return A [ArrayFields] instance containing only non-null key-value pairs, or [ArrayFields.EMPTY] if all values are null.
 *
 * Example:
 * ```
 * val optionalValue: String? = null
 * fieldsOfOptional(
 *     "key_1" to "value_1",
 *     "key_2" to optionalValue  // This pair will be excluded
 * )
 * ```
 */
fun fieldsOfOptional(vararg pairs: Pair<String, String?>): ArrayFields {
    val nonNull = pairs.filter { it.second != null }
    if (nonNull.isEmpty()) return ArrayFields.EMPTY
    val keys = Array(nonNull.size) { nonNull[it].first }
    val values = Array(nonNull.size) { nonNull[it].second!! }
    return ArrayFields(keys, values)
}

/**
 * Converts an Array<Pair<String, String>> to InternalFields.
 */
internal fun Array<Pair<String, String>>.toFields(): ArrayFields {
    if (isEmpty()) return ArrayFields.EMPTY
    val keys = Array(size) { this[it].first }
    val values = Array(size) { this[it].second }
    return ArrayFields(keys, values)
}

/**
 * Converts a Map<String, String> to InternalFields.
 * Handles the Java/Kotlin interop case where values might be null despite the signature.
 */
internal fun Map<String, String>.toFields(): ArrayFields {
    if (isEmpty()) return ArrayFields.EMPTY
    val validEntries =
        entries.filter { (k, v) ->
            @Suppress("SENSELESS_COMPARISON")
            k != null && v != null
        }
    if (validEntries.isEmpty()) return ArrayFields.EMPTY
    val keys = Array(validEntries.size) { validEntries[it].key }
    val values = Array(validEntries.size) { validEntries[it].value }
    return ArrayFields(keys, values)
}

/**
 * Converts a nullable Map<String, String> to [ArrayFields].
 * Returns [ArrayFields.EMPTY] if the map is null or empty.
 * Handles the Java/Kotlin interop case where values might be null despite the signature.
 */
fun Map<String, String>?.toFieldsOrEmpty(): ArrayFields {
    if (this == null || isEmpty()) return ArrayFields.EMPTY
    val validEntries =
        entries.filter { (k, v) ->
            @Suppress("SENSELESS_COMPARISON")
            k != null && v != null
        }
    if (validEntries.isEmpty()) return ArrayFields.EMPTY
    val keys = Array(validEntries.size) { validEntries[it].key }
    val values = Array(validEntries.size) { validEntries[it].value }
    return ArrayFields(keys, values)
}

/**
 * Combines multiple InternalFields into a single array.
 */
fun combineFields(vararg arrays: ArrayFields): ArrayFields {
    val totalSize = arrays.sumOf { it.size }
    if (totalSize == 0) return ArrayFields.EMPTY

    val keys = arrayOfNulls<String>(totalSize)
    val values = arrayOfNulls<String>(totalSize)
    var offset = 0
    for (array in arrays) {
        System.arraycopy(array.keys, 0, keys, offset, array.size)
        System.arraycopy(array.values, 0, values, offset, array.size)
        offset += array.size
    }
    return ArrayFields(keys.requireNoNulls(), values.requireNoNulls())
}

/**
 * Converts InternalFields to legacy Array<Field> for JNI calls that require it.
 */
internal fun ArrayFields.toLegacyJniFields(): Array<Field> {
    if (isEmpty()) return emptyArray()
    return Array(size) { i -> Field(keys[i], FieldValue.StringField(values[i])) }
}

/**
 * Builder for constructing InternalFields incrementally.
 */
internal class FieldArraysBuilder(
    initialCapacity: Int = 8,
) {
    private val keys = ArrayList<String>(initialCapacity)
    private val values = ArrayList<String>(initialCapacity)

    fun add(
        key: String,
        value: String,
    ): FieldArraysBuilder {
        keys.add(key)
        values.add(value)
        return this
    }

    fun addIfNotNull(
        key: String,
        value: String?,
    ): FieldArraysBuilder {
        if (value != null) {
            keys.add(key)
            values.add(value)
        }
        return this
    }

    fun addAll(other: ArrayFields): FieldArraysBuilder {
        for (i in 0 until other.size) {
            keys.add(other.keys[i])
            values.add(other.values[i])
        }
        return this
    }

    fun addAllPrefixed(
        prefix: String,
        other: ArrayFields,
    ): FieldArraysBuilder {
        for (i in 0 until other.size) {
            keys.add("$prefix${other.keys[i]}")
            values.add(other.values[i])
        }
        return this
    }

    fun build(): ArrayFields {
        if (keys.isEmpty()) return ArrayFields.EMPTY
        return ArrayFields(keys.toTypedArray(), values.toTypedArray())
    }
}
