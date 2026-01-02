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
 * Encapsulates information about an HTTP response event. This class is used to log the completion
 * of an HTTP request, including its duration, outcome, and associated metrics.
 *
 * Request and response events are logged separately but are linked. Each response event includes all
 * fields from its corresponding request event to facilitate correlation. To avoid field name
 * conflicts, request fields are prefixed with `_request.` within the response event. For example,
 * the `query` field from the request event becomes `_request.query` in the response event.
 *
 * @param request The original [HttpRequestInfo] that initiated this response.
 * @param response The [HttpResponse] object containing details like status code and errors.
 * @param durationMs The total duration of the HTTP request-response cycle in milliseconds.
 * @param metrics Optional [HttpRequestMetrics] providing detailed performance measurements.
 * @param extraFields A map of custom key-value pairs to be included in the event log.
 */
data class HttpResponseInfo
    @JvmOverloads
    constructor(
        val request: HttpRequestInfo,
        val response: HttpResponse,
        val durationMs: Long,
        var metrics: HttpRequestMetrics? = null,
        val extraFields: Map<String, String> = mapOf(),
    ) {
        internal val name: String = "HTTPResponse"

        internal val fields: InternalFieldsMap =
            run {
                // Collect out-of-the-box fields specific to HTTPResponse logs. The list consists of
                // HTTP specific fields such as host, path, or query and HTTP request performance
                // metrics such as DNS resolution time.
                val fields =
                    buildMap {
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
                                HttpField.PATH_TEMPLATE,
                                requestPathTemplate ?: it.template,
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
                            putOptional("_tls_duration_ms", it.tlsDurationMs)
                            putOptional("_tcp_duration_ms", it.tcpDurationMs)
                            putOptional("_fetch_init_duration_ms", it.fetchInitializationMs)
                            putOptional("_response_latency_ms", it.responseLatencyMs)
                            putOptional("_protocol", it.protocolName)
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
