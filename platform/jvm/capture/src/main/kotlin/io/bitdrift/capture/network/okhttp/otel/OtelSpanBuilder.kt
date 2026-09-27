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
    ): OtelSpan {
        val (protocolName, protocolVersion) = splitProtocol(data.metrics.protocolName)
        val error = data.response.error

        val attributes =
            OtelAttributes().apply {
                add("http.request.method", data.request.method)
                data.response.statusCode?.let { add("http.response.status_code", it) }
                add("url.full", data.httpRequest.url.toString())
                data.request.path
                    ?.template
                    ?.let { add("url.template", it) }
                add("server.address", data.httpRequest.url.host)
                add("server.port", data.httpRequest.url.port)
                data.metrics.requestBodyBytesSentCount?.let { add("http.request.body.size", it) }
                data.metrics.responseBodyBytesReceivedCount?.let { add("http.response.body.size", it) }
                add("error.type", error?.let { it::class.java.simpleName } ?: "")
                protocolName?.let { add("network.protocol.name", it) }
                protocolVersion?.let { add("network.protocol.version", it) }
                add("network.connection.type", networkAttributes.otelConnectionType())
                add("network.connection.subtype", networkAttributes.otelConnectionSubtype())
                add("network.carrier.name", networkAttributes.otelCarrierName())
                networkAttributes.otelCarrierMcc()?.let { add("network.carrier.mcc", it) }
                networkAttributes.otelCarrierMnc()?.let { add("network.carrier.mnc", it) }
                add("android.app.state", clientAttributes.currentAppState())
            }

        val startTimeUnixNano = data.startTimeEpochMs * NANOS_PER_MILLI
        val endTimeUnixNano = (data.startTimeEpochMs + data.durationMs) * NANOS_PER_MILLI

        return OtelSpan(
            traceId = data.traceContext.traceId,
            spanId = data.traceContext.spanId,
            name = "${data.request.method} ${data.request.path?.template ?: data.request.path?.value ?: ""}",
            startTimeUnixNano = startTimeUnixNano,
            endTimeUnixNano = endTimeUnixNano,
            isError = error != null,
            attributes = attributes,
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
