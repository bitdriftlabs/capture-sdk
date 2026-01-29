// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.providers.ArrayFields

private const val HEADERS_FIELD_KEY_PREFIX = "_headers"
private val DISALLOWED_HEADER_KEYS = setOf<String>("authorization", "proxy-authorization")

internal object HTTPHeaders {
    fun normalizeHeaders(headers: Map<String, String>): ArrayFields {
        if (headers.isEmpty()) return ArrayFields.EMPTY

        val keys = arrayOfNulls<String>(headers.size)
        val values = arrayOfNulls<String>(headers.size)
        var index = 0

        headers.forEach { (key, value) ->
            if (!DISALLOWED_HEADER_KEYS.contains(key.lowercase())) {
                keys[index] = "$HEADERS_FIELD_KEY_PREFIX.$key"
                values[index] = value
                index++
            }
        }

        if (index == 0) return ArrayFields.EMPTY
        if (index == headers.size) {
            return ArrayFields(keys.requireNoNulls(), values.requireNoNulls())
        }
        return ArrayFields(
            keys.copyOf(index).requireNoNulls(),
            values.copyOf(index).requireNoNulls(),
        )
    }
}
