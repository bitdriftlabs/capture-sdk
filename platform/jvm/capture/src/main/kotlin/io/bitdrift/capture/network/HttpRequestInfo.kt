// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.providers.FieldArraysBuilder
import io.bitdrift.capture.providers.Fields
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.fieldsOfOptional
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

        internal val fields: Fields by lazy {
            // Do not put body bytes count as a common field since response log has a more accurate
            // measurement of request' body count anyway.
            val baseFields =
                combineFields(
                    commonFields,
                    fieldsOf(SpanField.Key.TYPE to SpanField.Value.TYPE_START),
                )
            if (bytesExpectedToSendCount != null) {
                combineFields(
                    baseFields,
                    fieldsOf("_request_body_bytes_expected_to_send_count" to bytesExpectedToSendCount.toString()),
                )
            } else {
                baseFields
            }
        }

        internal val commonFields: Fields by lazy {
            val spanName = getSpanNameOverride(headers) ?: "_http"

            val baseFields =
                fieldsOf(
                    SpanField.Key.NAME to spanName,
                    SpanField.Key.ID to spanId.toString(),
                    "_method" to method,
                )

            val optionalFields =
                fieldsOfOptional(
                    HttpFieldKey.HOST to host,
                    HttpFieldKey.PATH to path?.value,
                    HttpFieldKey.QUERY to query,
                    HttpField.PATH_TEMPLATE to path?.template,
                )

            combineFields(
                extraFields.toFields(),
                baseFields,
                getOptionalHeaderSpanFields(headers),
                getOptionalGraphQlFields(headers),
                optionalFields,
            )
        }

        private fun getSpanNameOverride(headers: Map<String, String>?): String? {
            headers?.get("X-APOLLO-OPERATION-NAME")?.let {
                return "_graphql"
            }
            headers?.get("x-capture-span-key")?.let { spanKey ->
                val prefix = "x-capture-span-$spanKey"
                headers["$prefix-name"]?.let { name ->
                    return "_$name"
                }
            }
            return null
        }

        internal val matchingFields: Fields by lazy {
            headers?.let { HTTPHeaders.normalizeHeaders(it) } ?: Fields.EMPTY
        }

        /**
         * Extracts optional fields from headers based on the "x-capture-span-key" header.
         *
         * If the header is present, it constructs additional fields from other headers
         * matching the pattern "x-capture-span-{key}-field*".
         *
         * @param headers The map of headers from which fields are extracted.
         * @return InternalFields containing extracted span fields, or EMPTY if none found.
         */
        private fun getOptionalHeaderSpanFields(headers: Map<String, String>?): Fields {
            val spanKey = headers?.get("x-capture-span-key") ?: return Fields.EMPTY
            val prefix = "x-capture-span-$spanKey"
            val fieldPrefix = "$prefix-field"

            val matchingHeaders =
                headers.filter { (key, _) -> key.startsWith(fieldPrefix) }
            if (matchingHeaders.isEmpty()) return Fields.EMPTY

            val builder = FieldArraysBuilder(matchingHeaders.size)
            matchingHeaders.forEach { (key, value) ->
                val fieldKey = key.removePrefix(fieldPrefix).replace('-', '_')
                builder.add(fieldKey, value)
            }
            return builder.build()
        }

        /**
         * Extracts GraphQL operation fields from headers. Specific to Apollo3 Kotlin client.
         *
         * @param headers The map of headers from which fields are extracted.
         * @return InternalFields containing GraphQL operation fields, or EMPTY if none found.
         */
        private fun getOptionalGraphQlFields(headers: Map<String, String>?): Fields {
            val gqlOperationName =
                headers?.get("X-APOLLO-OPERATION-NAME") ?: return Fields.EMPTY

            val requiredFields =
                fieldsOf(
                    HttpField.PATH_TEMPLATE to "gql-$gqlOperationName",
                    "_operation_name" to gqlOperationName,
                )

            val optionalFields =
                fieldsOfOptional(
                    "_operation_type" to headers["X-APOLLO-OPERATION-TYPE"],
                    "_operation_id" to headers["X-APOLLO-OPERATION-ID"],
                )

            return combineFields(requiredFields, optionalFields)
        }
    }
