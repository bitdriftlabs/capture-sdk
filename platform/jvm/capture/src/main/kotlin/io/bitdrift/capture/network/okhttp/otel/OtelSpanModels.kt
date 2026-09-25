// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

import com.google.gson.annotations.SerializedName
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpRequestMetrics
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.okhttp.TraceContext
import okhttp3.Request

/**
 * Everything needed to build and export one OTel span for a single completed HTTP request.
 * Bundled together at the [io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListener]
 * call site so the export path only takes one argument.
 */
internal data class HttpSpanExportData(
    val traceContext: TraceContext,
    val request: HttpRequestInfo,
    val httpRequest: Request,
    val response: HttpResponse,
    val metrics: HttpRequestMetrics,
    val startTimeEpochMs: Long,
    val durationMs: Long,
)

/** Top-level OTLP/HTTP JSON export request body for `/v1/traces`. */
internal data class ResourceSpansPayload(
    @SerializedName("resourceSpans") val resourceSpans: List<ResourceSpans>,
)

internal data class ResourceSpans(
    @SerializedName("resource") val resource: Resource,
    @SerializedName("scopeSpans") val scopeSpans: List<ScopeSpans>,
)

internal data class Resource(
    @SerializedName("attributes") val attributes: List<KeyValue>,
)

internal data class ScopeSpans(
    @SerializedName("scope") val scope: Scope,
    @SerializedName("spans") val spans: List<Span>,
)

internal data class Scope(
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String? = null,
)

internal data class Span(
    @SerializedName("traceId") val traceId: String,
    @SerializedName("spanId") val spanId: String,
    @SerializedName("name") val name: String,
    @SerializedName("kind") val kind: Int,
    @SerializedName("startTimeUnixNano") val startTimeUnixNano: String,
    @SerializedName("endTimeUnixNano") val endTimeUnixNano: String,
    @SerializedName("attributes") val attributes: List<KeyValue>,
    @SerializedName("status") val status: SpanStatus,
) {
    internal companion object {
        /** `SPAN_KIND_CLIENT` per the OTLP `Span.SpanKind` enum. */
        const val KIND_CLIENT = 3
    }
}

internal data class SpanStatus(
    @SerializedName("code") val code: Int,
) {
    internal companion object {
        const val STATUS_CODE_OK = 1
        const val STATUS_CODE_ERROR = 2
    }
}

internal data class KeyValue(
    @SerializedName("key") val key: String,
    @SerializedName("value") val value: AnyValue,
) {
    internal companion object {
        fun of(
            key: String,
            value: String,
        ) = KeyValue(key, AnyValue(stringValue = value))

        fun of(
            key: String,
            value: Int,
        ) = KeyValue(key, AnyValue(intValue = value.toString()))

        fun of(
            key: String,
            value: Long,
        ) = KeyValue(key, AnyValue(intValue = value.toString()))
    }
}

/**
 * OTLP JSON encodes `int64`/`fixed64` values as strings to avoid precision loss, so [intValue]
 * is a `String`, not a numeric type, matching the wire format rather than the logical type.
 */
internal data class AnyValue(
    @SerializedName("stringValue") val stringValue: String? = null,
    @SerializedName("intValue") val intValue: String? = null,
)
