// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("UndocumentedPublicClass")
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

typealias LegacyFields = Map<String, String>

/**
 * A field provider is used to provide additional fields to each log event.
 *
 * It is invoked inline during logging, and so should therefore avoid doing expensive or blocking work.
 */
fun interface FieldProvider : () -> LegacyFields

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
 * Note to construct it please use, fieldsOf(vararg pairs: Pair<String, String>): Fields
 *
 * @property keys Array of field keys
 * @property values Array of field values corresponding to keys
 */
@Suppress("UndocumentedPublicFunction")
class Fields internal constructor(
    internal val keys: Array<String>,
    internal val values: Array<String>,
) {
    internal val size: Int get() = keys.size

    internal fun isEmpty(): Boolean = keys.isEmpty()

    internal fun isNotEmpty(): Boolean = keys.isNotEmpty()

    operator fun get(key: String): String? {
        val index = keys.indexOf(key)
        return if (index >= 0) values[index] else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fields) return false
        return keys.contentEquals(other.keys) && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = 31 * keys.contentHashCode() + values.contentHashCode()

    companion object {
        /** Provides an empty Fields instance **/
        val EMPTY = Fields(emptyArray(), emptyArray())
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
    stringFields: Fields,
    binaryFields: Array<Field>,
): Array<Field> {
    val totalSize = stringFields.size + binaryFields.size
    if (totalSize == 0) return emptyArray()

    val result = ArrayList<Field>(totalSize)
    for (i in 0 until stringFields.size) {
        result.add(Field(stringFields.keys[i], FieldValue.StringField(stringFields.values[i])))
    }
    result.addAll(binaryFields)
    return result.toTypedArray()
}

/**
 * Creates a Fields from pairs directly.
 *
 * Example:
 * ```
 * fieldsOf(
 *     "_app_exit_source" to "ApplicationExitInfo",
 *     "_app_exit_reason" to reason
 * )
 * ```
 */
fun fieldsOf(vararg pairs: Pair<String, String>): Fields {
    if (pairs.isEmpty()) return Fields.EMPTY
    val keys = Array(pairs.size) { pairs[it].first }
    val values = Array(pairs.size) { pairs[it].second }
    return Fields(keys, values)
}

/**
 * Creates Fields from pairs, filtering out null values.
 */
fun fieldsOfOptional(vararg pairs: Pair<String, String?>): Fields {
    val nonNull = pairs.filter { it.second != null }
    if (nonNull.isEmpty()) return Fields.EMPTY
    val keys = Array(nonNull.size) { nonNull[it].first }
    val values = Array(nonNull.size) { nonNull[it].second!! }
    return Fields(keys, values)
}

/**
 * Converts an Array<Pair<String, String>> to InternalFields.
 */
internal fun Array<Pair<String, String>>.toFields(): Fields {
    if (isEmpty()) return Fields.EMPTY
    val keys = Array(size) { this[it].first }
    val values = Array(size) { this[it].second }
    return Fields(keys, values)
}

/**
 * Converts a Map<String, String> to InternalFields.
 * Handles the Java/Kotlin interop case where values might be null despite the signature.
 */
internal fun Map<String, String>.toFields(): Fields {
    if (isEmpty()) return Fields.EMPTY
    val validEntries =
        entries.filter { (k, v) ->
            @Suppress("SENSELESS_COMPARISON")
            k != null && v != null
        }
    if (validEntries.isEmpty()) return Fields.EMPTY
    val keys = Array(validEntries.size) { validEntries[it].key }
    val values = Array(validEntries.size) { validEntries[it].value }
    return Fields(keys, values)
}

/**
 * Combines multiple InternalFields into a single array.
 */
fun combineFields(vararg arrays: Fields): Fields {
    val totalSize = arrays.sumOf { it.size }
    if (totalSize == 0) return Fields.EMPTY

    val keys = Array(totalSize) { "" }
    val values = Array(totalSize) { "" }
    var offset = 0
    for (array in arrays) {
        System.arraycopy(array.keys, 0, keys, offset, array.size)
        System.arraycopy(array.values, 0, values, offset, array.size)
        offset += array.size
    }
    return Fields(keys, values)
}

/**
 * Converts InternalFields to legacy Array<Field> for JNI calls that require it.
 */
internal fun Fields.toLegacyJniFields(): Array<Field> {
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

    fun addAll(other: Fields): FieldArraysBuilder {
        for (i in 0 until other.size) {
            keys.add(other.keys[i])
            values.add(other.values[i])
        }
        return this
    }

    fun addAllPrefixed(
        prefix: String,
        other: Fields,
    ): FieldArraysBuilder {
        for (i in 0 until other.size) {
            keys.add("$prefix${other.keys[i]}")
            values.add(other.values[i])
        }
        return this
    }

    fun build(): Fields {
        if (keys.isEmpty()) return Fields.EMPTY
        return Fields(keys.toTypedArray(), values.toTypedArray())
    }
}
