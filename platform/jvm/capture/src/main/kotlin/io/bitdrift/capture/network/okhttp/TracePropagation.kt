// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request

internal object TracePropagation {
    internal const val TRACE_ID_FIELD_KEY = "_trace_id"

    internal fun hasExistingTraceHeaders(request: Request): Boolean =
        request.header("traceparent") != null ||
            request.header("b3") != null ||
            request.header("X-B3-TraceId") != null

    internal fun extractSampledTraceId(request: Request): String? {
        val w3c = request.header("traceparent")
        if (w3c != null) {
            val parts = w3c.split("-")
            val flags = parts.getOrNull(3)?.toIntOrNull(16) ?: return null
            if (flags and 0x01 != 1) return null
            return parts.getOrNull(1)
        }

        val b3Single = request.header("b3")
        if (b3Single != null) {
            val parts = b3Single.split("-")
            val sampled = parts.getOrNull(2) ?: return null
            if (sampled != "1" && sampled != "d") return null
            return parts.getOrNull(0)
        }

        val b3TraceId = request.header("X-B3-TraceId")
        if (b3TraceId != null) {
            val sampled = request.header("X-B3-Sampled") ?: return null
            if (sampled != "1") return null
            return b3TraceId
        }

        return null
    }
}
