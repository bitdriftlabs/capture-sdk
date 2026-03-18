// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import java.security.SecureRandom

internal object CaptureTracing {
    internal const val TRACE_ID_FIELD_KEY = "_trace_id"

    private val random = SecureRandom()

    internal data class TraceContext(
        val traceId: String,
        val spanId: String,
        val traceparent: String,
        val b3: String,
    )

    internal fun newTraceContext(): TraceContext {
        val traceId = randomHex(16)
        val spanId = randomHex(8)
        return TraceContext(
            traceId = traceId,
            spanId = spanId,
            traceparent = "00-$traceId-$spanId-01",
            b3 = "$traceId-$spanId-1",
        )
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
