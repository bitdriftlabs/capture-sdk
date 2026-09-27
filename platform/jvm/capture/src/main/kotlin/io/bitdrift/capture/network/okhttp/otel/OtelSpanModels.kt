// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

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

/**
 * A flat list of OTLP attributes, laid out as parallel arrays so it can cross the JNI boundary
 * without per-attribute objects. Values are always carried as strings and reinterpreted by the
 * Rust side according to the matching entry in [valueTypes]; adding a new attribute is therefore
 * a pure Kotlin change, never a JNI signature change.
 */
internal class OtelAttributes {
    private val keyList = mutableListOf<String>()
    private val valueList = mutableListOf<String>()
    private val typeList = mutableListOf<Byte>()

    val keys: Array<String> get() = keyList.toTypedArray()
    val values: Array<String> get() = valueList.toTypedArray()
    val valueTypes: ByteArray get() = typeList.toByteArray()

    fun add(
        key: String,
        value: String,
    ) = append(key, value, TYPE_STRING)

    fun add(
        key: String,
        value: Int,
    ) = append(key, value.toString(), TYPE_INT)

    fun add(
        key: String,
        value: Long,
    ) = append(key, value.toString(), TYPE_INT)

    private fun append(
        key: String,
        value: String,
        type: Byte,
    ) {
        keyList.add(key)
        valueList.add(value)
        typeList.add(type)
    }

    private companion object {
        // Must match OTLP_ATTRIBUTE_TYPE_* in platform/jvm/core/src/ffi.rs.
        const val TYPE_STRING: Byte = 0
        const val TYPE_INT: Byte = 1
    }
}

/** The span-specific fields handed to `CaptureJniLibrary.buildOtelSpanPayload`. */
internal data class OtelSpan(
    val traceId: String,
    val spanId: String,
    val name: String,
    val startTimeUnixNano: Long,
    val endTimeUnixNano: Long,
    val isError: Boolean,
    val attributes: OtelAttributes,
)
