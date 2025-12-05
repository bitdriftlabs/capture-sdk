@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue

/**
 * Converts a Map<String, String> to InternalFieldsArray for test assertions.
 */
fun Map<String, String>.toInternalFieldsArray(): InternalFields =
    map { (key, value) -> Field(key, value.toFieldValue()) }.toTypedArray()

/**
 * Gets a value from InternalFieldsArray by key.
 */
operator fun InternalFields.get(key: String): FieldValue? = firstOrNull { it.key == key }?.value

/**
 * Converts InternalFieldsArray to Map<String, String> for test comparisons.
 */
fun InternalFields.toStringMap(): Map<String, String> = associate { it.key to it.value.toString() }

/**
 * Checks if InternalFieldsArray contains a key.
 */
fun InternalFields.containsKey(key: String): Boolean = any { it.key == key }

/**
 * Converts a Map<String, FieldValue> into an Array<Field> for efficient JNI transfer.
 */
fun Map<String, String>.toInternalFields(): InternalFields {
    if (isEmpty()) return EMPTY_INTERNAL_FIELDS
    val result = arrayOfNulls<Field>(size)
    var i = 0
    for ((key, value) in this) {
        result[i++] = Field(key, value.toFieldValue())
    }
    @Suppress("UNCHECKED_CAST")
    return result as InternalFields
}
