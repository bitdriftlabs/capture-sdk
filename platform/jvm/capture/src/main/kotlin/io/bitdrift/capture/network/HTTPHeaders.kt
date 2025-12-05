// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.toFieldValue

private const val HEADERS_FIELD_KEY_PREFIX = "_headers"
private val DISALLOWED_HEADER_KEYS = setOf<String>("authorization", "proxy-authorization")

internal object HTTPHeaders {
    fun normalizeHeaders(headers: Map<String, String>): InternalFields =
        buildList(headers.size) {
            headers.forEach { (key, value) ->
                if (!DISALLOWED_HEADER_KEYS.contains(key.lowercase())) {
                    add(Field("$HEADERS_FIELD_KEY_PREFIX.$key", value.toFieldValue()))
                }
            }
        }.toTypedArray()
}
