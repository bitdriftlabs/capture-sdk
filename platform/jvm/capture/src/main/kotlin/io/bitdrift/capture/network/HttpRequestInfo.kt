// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.InternalFieldsMap
import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFields
import java.util.UUID

internal object HttpFieldKey {
    const val HOST = "_host"
    const val PATH = "_path"
    const val PATH_TEMPLATE = "_path_template"
    const val QUERY = "_query"
}

/**
 * Class that encapsulates network request information. method param is required, rest are optional
 */
data class HttpRequestInfo @JvmOverloads constructor(
    private val method: String,
    private val host: String? = null,
    internal val path: HttpUrlPath? = null,
    private val query: String? = null,
    private val headers: Map<String, String>? = null,
    private val bytesExpectedToSendCount: Long? = null,
    private val spanId: UUID = UUID.randomUUID(),
    private val extraFields: Map<String, String> = mapOf(),
) {
    internal val name: String = "HTTPRequest"

    internal val fields: InternalFieldsMap by lazy {
        buildMap {
            putAll(extraFields.toFields())
            putAll(coreFields.toFields())
            put(SpanField.Key.ID, FieldValue.StringField(spanId.toString()))
            put(SpanField.Key.NAME, FieldValue.StringField("_http"))
            put(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_START))
        }
    }

    /**
     * Fields that describe the network request.
     */
    val coreFields: Map<String, String> by lazy {
        buildMap {
            put("_method", method)
            host?.let { put("_host", it) }
            path?.let { put("_path", it.value) }
            query?.let { put("_query", it) }
            path?.template?.let { put(HttpFieldKey.PATH_TEMPLATE, it) }
            bytesExpectedToSendCount?.let { put("_request_body_bytes_expected_to_send_count", it.toString()) }
        }
    }

    internal val matchingFields: InternalFieldsMap = headers?.let { HTTPHeaders.normalizeHeaders(it) }.toFields()
}
