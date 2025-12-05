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

        internal val fields: InternalFields =
            run {
                // Collect out-of-the-box fields specific to HTTPResponse logs. The list consists of
                // HTTP specific fields such as host, path, or query and HTTP request performance
                // metrics such as DNS resolution time.
                val fields =
                    buildList {
                        add(Field(SpanField.Key.TYPE, FieldValue.StringField(SpanField.Value.TYPE_END)))
                        add(
                            Field(
                                SpanField.Key.DURATION,
                                FieldValue.StringField(durationMs.toString()),
                            ),
                        )
                        add(
                            Field(
                                SpanField.Key.RESULT,
                                FieldValue.StringField(response.result.name.lowercase()),
                            ),
                        )
                        response.statusCode?.let {
                            add(
                                Field(
                                    "_status_code",
                                    FieldValue.StringField(it.toString()),
                                ),
                            )
                        }
                        response.error?.let { error ->
                            add(
                                Field(
                                    "_error_type",
                                    FieldValue.StringField(error::class.java.simpleName),
                                ),
                            )
                        }
                        response.error?.let { error ->
                            add(
                                Field(
                                    "_error_message",
                                    FieldValue.StringField(error.message.orEmpty()),
                                ),
                            )
                        }
                        response.host?.let { add(Field(HttpFieldKey.HOST, FieldValue.StringField(it))) }
                        response.path?.value?.let {
                            add(
                                Field(
                                    HttpFieldKey.PATH,
                                    FieldValue.StringField(it),
                                ),
                            )
                        }
                        response.query?.let {
                            add(
                                Field(
                                    HttpFieldKey.QUERY,
                                    FieldValue.StringField(it),
                                ),
                            )
                        }

                        response.path?.let {
                            val requestPathTemplate =
                                if (request.path?.value == it.value) {
                                    // If the path between request and response did not change and an explicit path
                                    // template was provided as part of a request use it as path template on a response.
                                    request.path.template
                                } else {
                                    null
                                }

                            (requestPathTemplate ?: it.template)?.let { template ->
                                add(Field(HttpField.PATH_TEMPLATE, FieldValue.StringField(template)))
                            }
                        }

                        metrics?.let {
                            add(
                                Field(
                                    "_request_body_bytes_sent_count",
                                    FieldValue.StringField(it.requestBodyBytesSentCount.toString()),
                                ),
                            )
                            add(
                                Field(
                                    "_response_body_bytes_received_count",
                                    FieldValue.StringField(it.responseBodyBytesReceivedCount.toString()),
                                ),
                            )
                            add(
                                Field(
                                    "_request_headers_bytes_count",
                                    FieldValue.StringField(it.requestHeadersBytesCount.toString()),
                                ),
                            )
                            add(
                                Field(
                                    "_response_headers_bytes_count",
                                    FieldValue.StringField(it.responseHeadersBytesCount.toString()),
                                ),
                            )
                            it.dnsResolutionDurationMs?.let {
                                add(
                                    Field(
                                        "_dns_resolution_duration_ms",
                                        FieldValue.StringField(it.toString()),
                                    ),
                                )
                            }
                            it.tlsDurationMs?.let {
                                add(
                                    Field(
                                        "_tls_duration_ms",
                                        FieldValue.StringField(it.toString()),
                                    ),
                                )
                            }
                            it.tcpDurationMs?.let {
                                add(
                                    Field(
                                        "_tcp_duration_ms",
                                        FieldValue.StringField(it.toString()),
                                    ),
                                )
                            }
                            it.fetchInitializationMs?.let {
                                add(
                                    Field(
                                        "_fetch_init_duration_ms",
                                        FieldValue.StringField(it.toString()),
                                    ),
                                )
                            }
                            it.responseLatencyMs?.let {
                                add(
                                    Field(
                                        "_response_latency_ms",
                                        FieldValue.StringField(it.toString()),
                                    ),
                                )
                            }
                            it.protocolName?.let { add(Field("_protocol", FieldValue.StringField(it))) }
                        }
                    }.toTypedArray()

                // Combine fields in the increasing order of their priority as the latter fields
                // override the former ones in the case of field name conflicts.
                combineFields(extraFields.toFields(), request.commonFields, fields)
            }

        internal val matchingFields: InternalFields =
            combineFields(
                buildList(request.fields.size + request.matchingFields.size) {
                    request.fields.forEach { add(Field("_request.${it.key}", it.value)) }
                    request.matchingFields.forEach { add(Field("_request.${it.key}", it.value)) }
                }.toTypedArray(),
                response.headers?.let { HTTPHeaders.normalizeHeaders(it) } ?: EMPTY_INTERNAL_FIELDS,
            )
    }
