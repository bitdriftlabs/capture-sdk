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
        // Do not put body bytes count as a common field since response log has a more accurate
        // measurement of request' body count anyway.
        buildMap {
            putAll(commonFields)
            putOptional("_request_body_bytes_expected_to_send_count", bytesExpectedToSendCount)
        }
    }

    internal val commonFields: InternalFieldsMap by lazy {
        buildMap {
            putAll(extraFields.toFields())
            putOptionalHeaderSpanFields(headers)
            put(SpanField.Key.ID, FieldValue.StringField(spanId.toString()))
            put(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_START))
            put("_method", FieldValue.StringField(method))
            putOptional(HttpFieldKey.HOST, host)
            putOptional(HttpFieldKey.PATH, path?.value)
            putOptional(HttpFieldKey.QUERY, query)
            path?.let {
                it.template?.let {
                    put(HttpFieldKey.PATH_TEMPLATE, FieldValue.StringField(it))
                }
            }
        }
    }

    internal val matchingFields: InternalFieldsMap = headers?.let { HTTPHeaders.normalizeHeaders(it) }.toFields()

    /**
     * Adds optional fields to the mutable map based on the provided headers.
     *
     * This function checks for the presence of the "x-capture-span-key" header.
     * If the header is present, it constructs a span name and additional fields from other headers
     * and adds them to the map. If the header is not present, it adds a default span name.
     *
     * @param headers The map of headers from which fields are extracted.
     */
    private fun MutableMap<String, FieldValue>.putOptionalHeaderSpanFields(headers: Map<String, String>?) {
        headers?.get("x-capture-span-key")?.let { spanKey ->
            val prefix = "x-capture-span-$spanKey"
            val spanName = "_" + headers["$prefix-name"]
            put(SpanField.Key.NAME, FieldValue.StringField(spanName))
            val fieldPrefix = "$prefix-field"
            headers.forEach { (key, value) ->
                if (key.startsWith(fieldPrefix)) {
                    val fieldKey = key.removePrefix(fieldPrefix).replace('-', '_')
                    put(fieldKey, FieldValue.StringField(value))
                }
            }
        } ?: run {
            // Default span name is simply http
            put(SpanField.Key.NAME, FieldValue.StringField("_http"))
        }
    }
}
