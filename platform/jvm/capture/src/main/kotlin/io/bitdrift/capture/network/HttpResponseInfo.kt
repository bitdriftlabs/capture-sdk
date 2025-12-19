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
import io.bitdrift.capture.providers.fieldOf
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.fieldsOfOptional
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

        internal val fields: Fields =
            run {
                // Collect out-of-the-box fields specific to HTTPResponse logs. The list consists of
                // HTTP specific fields such as host, path, or query and HTTP request performance
                // metrics such as DNS resolution time.
                val baseFields =
                    fieldsOf(
                        SpanField.Key.TYPE to SpanField.Value.TYPE_END,
                        SpanField.Key.DURATION to durationMs.toString(),
                        SpanField.Key.RESULT to response.result.name.lowercase(),
                    )

                val statusFields =
                    response.statusCode?.let {
                        fieldOf("_status_code", it.toString())
                    } ?: Fields.EMPTY

                val errorFields =
                    response.error?.let { error ->
                        fieldsOf(
                            "_error_type" to error::class.java.simpleName,
                            "_error_message" to error.message.orEmpty(),
                        )
                    } ?: Fields.EMPTY

                val responseOverrideFields =
                    fieldsOfOptional(
                        HttpFieldKey.HOST to response.host,
                        HttpFieldKey.PATH to response.path?.value,
                        HttpFieldKey.QUERY to response.query,
                    )

                val pathTemplateFields =
                    response.path?.let { respPath ->
                        // If the path between request and response did not change and an explicit path
                        // template was provided as part of a request use it as path template on a response.
                        val template =
                            if (request.path?.value == respPath.value) {
                                request.path.template ?: respPath.template
                            } else {
                                respPath.template
                            }
                        template?.let { fieldOf(HttpField.PATH_TEMPLATE, it) }
                    } ?: Fields.EMPTY

                val metricsFields =
                    metrics?.let { m ->
                        combineFields(
                            fieldsOf(
                                "_request_body_bytes_sent_count" to m.requestBodyBytesSentCount.toString(),
                                "_response_body_bytes_received_count" to m.responseBodyBytesReceivedCount.toString(),
                                "_request_headers_bytes_count" to m.requestHeadersBytesCount.toString(),
                                "_response_headers_bytes_count" to m.responseHeadersBytesCount.toString(),
                            ),
                            fieldsOfOptional(
                                "_dns_resolution_duration_ms" to m.dnsResolutionDurationMs?.toString(),
                                "_tls_duration_ms" to m.tlsDurationMs?.toString(),
                                "_tcp_duration_ms" to m.tcpDurationMs?.toString(),
                                "_fetch_init_duration_ms" to m.fetchInitializationMs?.toString(),
                                "_response_latency_ms" to m.responseLatencyMs?.toString(),
                                "_protocol" to m.protocolName,
                            ),
                        )
                    } ?: Fields.EMPTY

                // Combine fields in the increasing order of their priority as the latter fields
                // override the former ones in the case of field name conflicts.
                combineFields(
                    extraFields.toFields(),
                    request.commonFields,
                    baseFields,
                    statusFields,
                    errorFields,
                    responseOverrideFields,
                    pathTemplateFields,
                    metricsFields,
                )
            }

        internal val matchingFields: Fields =
            run {
                val builder = FieldArraysBuilder(request.fields.size + request.matchingFields.size + 10)
                builder.addAllPrefixed("_request.", request.fields)
                builder.addAllPrefixed("_request.", request.matchingFields)
                val requestPrefixedFields = builder.build()

                val responseHeaders =
                    response.headers?.let { HTTPHeaders.normalizeHeaders(it) } ?: Fields.EMPTY
                combineFields(requestPrefixedFields, responseHeaders)
            }
    }
