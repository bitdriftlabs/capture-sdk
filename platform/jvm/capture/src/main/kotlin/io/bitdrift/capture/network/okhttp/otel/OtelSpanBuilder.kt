// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.NetworkAttributes

/**
 * Builds a single OTel `CLIENT` span for a completed HTTP request, using the exact trace/span ID
 * already injected into that request's outbound trace header.
 *
 * Field-by-field OTel semantic convention references and stability status are documented in the
 * BIT-9050 PRD appendix; this mirrors that table.
 */
internal object OtelSpanBuilder {
    fun build(
        data: HttpSpanExportData,
        networkAttributes: NetworkAttributes,
        clientAttributes: ClientAttributes,
    ): Span {
        val (protocolName, protocolVersion) = splitProtocol(data.metrics.protocolName)
        val error = data.response.error

        val attributes =
            buildList {
                add(KeyValue.of("http.request.method", data.request.method))
                data.response.statusCode?.let { add(KeyValue.of("http.response.status_code", it)) }
                add(KeyValue.of("url.full", data.httpRequest.url.toString()))
                data.request.path?.template?.let { add(KeyValue.of("url.template", it)) }
                add(KeyValue.of("server.address", data.httpRequest.url.host))
                add(KeyValue.of("server.port", data.httpRequest.url.port))
                data.metrics.requestBodyBytesSentCount?.let { add(KeyValue.of("http.request.body.size", it)) }
                data.metrics.responseBodyBytesReceivedCount?.let { add(KeyValue.of("http.response.body.size", it)) }
                add(KeyValue.of("error.type", error?.let { it::class.java.simpleName } ?: ""))
                protocolName?.let { add(KeyValue.of("network.protocol.name", it)) }
                protocolVersion?.let { add(KeyValue.of("network.protocol.version", it)) }
                add(KeyValue.of("network.connection.type", networkAttributes.otelConnectionType()))
                add(KeyValue.of("network.connection.subtype", networkAttributes.otelConnectionSubtype()))
                add(KeyValue.of("network.carrier.name", networkAttributes.otelCarrierName()))
                networkAttributes.otelCarrierMcc()?.let { add(KeyValue.of("network.carrier.mcc", it)) }
                networkAttributes.otelCarrierMnc()?.let { add(KeyValue.of("network.carrier.mnc", it)) }
                add(KeyValue.of("android.app.state", clientAttributes.currentAppState()))
            }

        val startTimeUnixNano = data.startTimeEpochMs * NANOS_PER_MILLI
        val endTimeUnixNano = (data.startTimeEpochMs + data.durationMs) * NANOS_PER_MILLI

        return Span(
            traceId = data.traceContext.traceId,
            spanId = data.traceContext.spanId,
            name = "${data.request.method} ${data.request.path?.template ?: data.request.path?.value ?: ""}",
            kind = Span.KIND_CLIENT,
            startTimeUnixNano = startTimeUnixNano.toString(),
            endTimeUnixNano = endTimeUnixNano.toString(),
            attributes = attributes,
            status =
                SpanStatus(
                    code = if (error != null) SpanStatus.STATUS_CODE_ERROR else SpanStatus.STATUS_CODE_OK,
                ),
        )
    }

    /** Splits the combined `_protocol` value (e.g. `"h2"`, `"http/1.1"`) into name/version. */
    private fun splitProtocol(protocol: String?): Pair<String?, String?> {
        val value = protocol ?: return null to null
        return when {
            value == "h2" -> "http" to "2"
            value == "h2_prior_knowledge" -> "http" to "2"
            value == "h3" -> "http" to "3"
            value.startsWith("http/") -> "http" to value.removePrefix("http/")
            else -> value to null
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
