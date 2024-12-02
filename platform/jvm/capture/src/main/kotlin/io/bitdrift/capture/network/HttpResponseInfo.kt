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

/**
 * Class that encapsulates the information about the HTTP response.
 * `request`, `response`, and `durationMs` parameters are required; the rest are optional.
 *
 * While response and request logs are logged separately, they are interconnected, and every
 * response log contains all of the fields of the corresponding request log for matching purposes.
 * To ensure key field uniqueness, the names of request log fields attached to the response log are
 * prefixed with the "_request." string, so the field "query" from the request log becomes "_start.query" in
 * the response log.
 *
 * `Host`, `path`, and `query` attributes captured by the request log are shared with the response log, with
 * an option to override them by providing these attributes in the `HTTPResponse`. Refer to `HTTPResponse`
 * for more details.
 */
data class HttpResponseInfo @JvmOverloads constructor(
    private val request: HttpRequestInfo,
    private val response: HttpResponse,
    private val durationMs: Long,
    private var metrics: HttpRequestMetrics? = null,
    private val extraFields: Map<String, String> = mapOf(),
) {
    internal val name: String = "HTTPResponse"

    internal val fields: InternalFieldsMap by lazy {
        buildMap {
            // Combine fields in the increasing order of their priority as the latter fields
            // override the former ones in the case of field name conflicts.
            putAll(extraFields.toFields())
            putAll(request.fields)
            putAll(coreFields.toFields())
            put(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_END))
            put(
                SpanField.Key.DURATION,
                FieldValue.StringField(durationMs.toString()),
            )
            put(
                SpanField.Key.RESULT,
                FieldValue.StringField(response.result.name.lowercase()),
            )
        }
    }

    /**
     * Fields that describe the network response result.
     */
    val coreFields: Map<String, String> by lazy {
        // Collect out-of-the-box fields specific to HTTPResponse logs. The list consists of
        // HTTP specific fields such as host, path, or query and HTTP request performance
        // metrics such as DNS resolution time.
        buildMap {
            response.statusCode?.let { put("_status_code", it.toString()) }
            response.error?.let {
                put(
                    "_error_type",
                    it::javaClass.get().simpleName,
                )
                put(
                    "_error_message",
                    it.message.orEmpty(),
                )
            }
            response.host?.let { put(HttpFieldKey.HOST, it) }
            response.path?.value?.let { put(HttpFieldKey.PATH, it) }
            response.query?.let { put(HttpFieldKey.QUERY, it) }

            response.path?.let { it ->
                val requestPathTemplate =
                    if (request.path?.value == it.value) {
                        // If the path between request and response did not change and an explicit path
                        // template was provided as part of a request use it as path template on a response.
                        request.path.template
                    } else {
                        null
                    }

                requestPathTemplate ?: it.template?.let { put(HttpFieldKey.PATH_TEMPLATE, it) }
            }

            metrics?.let<HttpRequestMetrics, Unit> { metrics ->
                put(
                    "_request_body_bytes_sent_count",
                    metrics.requestBodyBytesSentCount.toString(),
                )
                put(
                    "_response_body_bytes_received_count",
                    metrics.responseBodyBytesReceivedCount.toString(),
                )
                put(
                    "_request_headers_bytes_count",
                    metrics.requestHeadersBytesCount.toString(),
                )
                put(
                    "_response_headers_bytes_count",
                    metrics.responseHeadersBytesCount.toString(),
                )
                metrics.dnsResolutionDurationMs?.let { put("_dns_resolution_duration_ms", it.toString()) }
            }
        }
    }

    internal val matchingFields: InternalFieldsMap =
        request.fields.mapKeys { "_request.${it.key}" } +
            request.matchingFields.mapKeys { "_request.${it.key}" } +
            response.headers?.let { HTTPHeaders.normalizeHeaders(it) }.toFields()
}
