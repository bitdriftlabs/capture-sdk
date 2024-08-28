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

    internal val fields: InternalFieldsMap =
        run {
            // Collect out-of-the-box fields specific to HTTPResponse logs. The list consists of
            // HTTP specific fields such as host, path, or query and HTTP request performance
            // metrics such as DNS resolution time.
            val fields = buildMap {
                this.put(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_END))
                this.put(
                    SpanField.Key.DURATION,
                    FieldValue.StringField(durationMs.toString()),
                )
                this.put(
                    SpanField.Key.RESULT,
                    FieldValue.StringField(response.result.name.lowercase()),
                )
                putOptional("_status_code", response.statusCode)
                putOptional(
                    "_error_type",
                    response.error,
                ) { it::javaClass.get().simpleName }
                putOptional(
                    "_error_message",
                    response.error,
                ) { it.message.orEmpty() }
                putOptional(HttpFieldKey.HOST, response.host)
                putOptional(HttpFieldKey.PATH, response.path?.value)
                putOptional(HttpFieldKey.QUERY, response.query)

                response.path?.let {
                    val requestPathTemplate =
                        if (request.path?.value == it.value) {
                            // If the path between request and response did not change and an explicit path
                            // template was provided as part of a request use it as path template on a response.
                            request.path.template
                        } else {
                            null
                        }

                    putOptional(
                        HttpFieldKey.PATH_TEMPLATE,
                        requestPathTemplate?.let { it } ?: it.template?.let { it },
                    )
                }

                metrics?.let<HttpRequestMetrics, Unit> {
                    this.put(
                        "_request_body_bytes_sent_count",
                        FieldValue.StringField(it.requestBodyBytesSentCount.toString()),
                    )
                    this.put(
                        "_response_body_bytes_received_count",
                        FieldValue.StringField(it.responseBodyBytesReceivedCount.toString()),
                    )
                    this.put(
                        "_request_headers_bytes_count",
                        FieldValue.StringField(it.requestHeadersBytesCount.toString()),
                    )
                    this.put(
                        "_response_headers_bytes_count",
                        FieldValue.StringField(it.responseHeadersBytesCount.toString()),
                    )
                    putOptional("_dns_resolution_duration_ms", it.dnsResolutionDurationMs)
                }
            }

            // Combine fields in the increasing order of their priority as the latter fields
            // override the former ones in the case of field name conflicts.
            extraFields.toFields() + request.commonFields + fields
        }

    internal val matchingFields: InternalFieldsMap =
        request.fields.mapKeys { "_request.${it.key}" } +
            request.matchingFields.mapKeys { "_request.${it.key}" } +
            response.headers?.let { HTTPHeaders.normalizeHeaders(it) }.toFields()
}
