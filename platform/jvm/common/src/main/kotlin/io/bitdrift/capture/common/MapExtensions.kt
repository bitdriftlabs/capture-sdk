// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.common

/**
 * Handles potential Java Interop issues where a Map with null key values could
 * be passed in Java. Below a Java example where kotlin will see message value as non-null
 * (See also BIT-5914)
 *
 *  IllegalStateException exception = new IllegalStateException();
 *  HashMap<String, String> fields = new HashMap<>();
 *  fields.put("exception_message", exception.getMessage()); // exception.getMessage will return null
 *
 * NOTE: Suppressing warning about null-checks that appear redundant because
 * Kotlin's compiler assumes non-null keys/values, but in practice,
 * due to Java interop, null values may still appear in the map.
 */
@Suppress("SENSELESS_COMPARISON")
fun MutableMap<String, String>.putAllSafely(fields: Map<String, String>?) {
    if (fields == null) return
    for (entry in fields.entries) {
        val key = entry.key
        val value = entry.value
        if (key != null && value != null) {
            this[key] = value
        }
    }
}
