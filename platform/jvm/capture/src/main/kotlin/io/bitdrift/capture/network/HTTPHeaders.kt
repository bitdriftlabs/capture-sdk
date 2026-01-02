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
        val keys = mutableListOf<String>()
        val values = mutableListOf<String>()
        headers.forEach { (key, value) ->
            if (!DISALLOWED_HEADER_KEYS.contains(key.lowercase())) {
                keys.add("$HEADERS_FIELD_KEY_PREFIX.$key")
                values.add(value)
            }
        }
        if (keys.isEmpty()) return ArrayFields.EMPTY
        return ArrayFields(keys.toTypedArray(), values.toTypedArray())
    }
}
