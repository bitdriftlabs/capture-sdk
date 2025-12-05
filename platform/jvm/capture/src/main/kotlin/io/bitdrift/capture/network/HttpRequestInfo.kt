// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.EMPTY_INTERNAL_FIELDS
import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.toFields
import java.util.UUID

internal object HttpFieldKey {
    const val HOST = "_host"
    const val PATH = "_path"
    const val QUERY = "_query"
}

/**
 * Constant field keys for HTTP events.
 */
object HttpField {
    /**
     * A templated hint of an HTTP request path, where dynamic parts (like IDs) are replaced
     * with placeholders. This is used for grouping and aggregating network requests.
     * For example, `/users/123/profile` might become `/users/{userId}/profile`.
     */
    const val PATH_TEMPLATE = "_path_template"
}

/**
 * Encapsulates information about an outgoing HTTP request. This class is used to log the start
 * of a network operation.
 *
 * An `HttpRequestInfo` object is typically created and passed to the Bitdrift SDK when a network
 * request is initiated. It is later paired with a corresponding `HttpResponseInfo` object
 * (matched by `spanId`) to form a complete span representing the entire network call.
 *
 * The `@JvmOverloads` annotation allows for convenient construction from Java with default values
 * for optional parameters.
 *
 * @param method The HTTP method of the request (e.g., "GET", "POST"). This is a required field.
 * @param host The host name or IP address of the server. Example: "api.bitdrift.io".
 * @param path The path of the request URL, which can optionally include a template for aggregation.
 *             See [HttpUrlPath] for more details. Example: `/v1/users/123`.
 * @param query The query string part of the URL, without the leading '?'. Example: "id=123&type=user".
 * @param headers A map of HTTP request headers.
 * @param bytesExpectedToSendCount The expected size of the request body in bytes, if known.
 * @param spanId A unique identifier for this request-response pair. If not provided, a random UUID
 *               will be generated. This ID is crucial for matching the request with its response.
 * @param extraFields A map of any additional custom key-value pairs to be included in the log event.
 */
data class HttpRequestInfo
    @JvmOverloads
    constructor(
        val method: String,
        val host: String? = null,
        val path: HttpUrlPath? = null,
        val query: String? = null,
        val headers: Map<String, String>? = null,
        val bytesExpectedToSendCount: Long? = null,
        val spanId: UUID = UUID.randomUUID(),
        val extraFields: Map<String, String> = mapOf(),
    ) {
        internal val name: String = "HTTPRequest"

        internal val fields: InternalFields by lazy {
            combineFields(
                commonFields,
                if (bytesExpectedToSendCount != null) {
                    fieldsOf("_request_body_bytes_expected_to_send_count" to bytesExpectedToSendCount.toString())
                } else {
                    EMPTY_INTERNAL_FIELDS
                },
            )
        }

        internal val commonFields: InternalFields by lazy {
            buildList {
                addAll(extraFields.toFields())
                add(Field(SpanField.Key.NAME, FieldValue.StringField("_http")))
                addOptionalHeaderSpanFields(headers)
                addOptionalGraphQlHeaders(headers)
                add(Field(SpanField.Key.ID, FieldValue.StringField(spanId.toString())))
                add(Field(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_START)))
                add(Field("_method", FieldValue.StringField(method)))
                host?.let { add(Field(HttpFieldKey.HOST, FieldValue.StringField(it))) }
                path?.value?.let { add(Field(HttpFieldKey.PATH, FieldValue.StringField(it))) }
                query?.let { add(Field(HttpFieldKey.QUERY, FieldValue.StringField(it))) }
                path?.template?.let { add(Field(HttpField.PATH_TEMPLATE, FieldValue.StringField(it))) }
            }.toTypedArray()
        }

        internal val matchingFields: InternalFields by lazy {
            headers?.let { HTTPHeaders.normalizeHeaders(it) } ?: EMPTY_INTERNAL_FIELDS
        }

        /**
         * Adds optional fields to the mutable list based on the provided headers.
         *
         * This function checks for the presence of the "x-capture-span-key" header.
         * If the header is present, it constructs a span name and additional fields from other headers
         * and adds them to the list. If the header is not present, it adds a default span name.
         *
         * @param headers The map of headers from which fields are extracted.
         */
        private fun MutableList<Field>.addOptionalHeaderSpanFields(headers: Map<String, String>?) {
            headers?.get("x-capture-span-key")?.let { spanKey ->
                val prefix = "x-capture-span-$spanKey"
                val spanName = "_" + headers["$prefix-name"]
                add(Field(SpanField.Key.NAME, FieldValue.StringField(spanName)))
                val fieldPrefix = "$prefix-field"
                headers.forEach { (key, value) ->
                    if (key.startsWith(fieldPrefix)) {
                        val fieldKey = key.removePrefix(fieldPrefix).replace('-', '_')
                        add(Field(fieldKey, FieldValue.StringField(value)))
                    }
                }
            }
        }

        /**
         * Best effort to extract graphQL operation name from the headers, this is specific to apollo3 kotlin client
         *
         * @param headers The map of headers from which fields are extracted.
         */
        private fun MutableList<Field>.addOptionalGraphQlHeaders(headers: Map<String, String>?) {
            headers?.get("X-APOLLO-OPERATION-NAME")?.let { gqlOperationName ->
                add(Field(HttpField.PATH_TEMPLATE, FieldValue.StringField("gql-$gqlOperationName")))
                add(Field("_operation_name", FieldValue.StringField(gqlOperationName)))
                headers["X-APOLLO-OPERATION-TYPE"]?.let { gqlOperationKey ->
                    add(Field("_operation_type", FieldValue.StringField(gqlOperationKey)))
                }
                headers["X-APOLLO-OPERATION-ID"]?.let { gqlOperationId ->
                    add(Field("_operation_id", FieldValue.StringField(gqlOperationId)))
                }
                add(Field(SpanField.Key.NAME, FieldValue.StringField("_graphql")))
            }
        }
    }
