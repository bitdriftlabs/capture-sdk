// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import java.security.SecureRandom

/**
 * Generates a [TraceContext] which will be added to network headers. This is gated by
 * [io.bitdrift.capture.common.RuntimeStringConfig.TRACE_PROPAGATION_MODE].
 *
 * Sampled flag is hardcoded to 1 since traces are only generated when tracing is active.
 */
internal class TraceContextFactory {
    private val secureRandom = SecureRandom()

    internal fun generateTraceContext(): TraceContext {
        val traceId = generateRandomHex(16)
        val spanId = generateRandomHex(8)
        return TraceContext(
            traceId = traceId,
            spanId = spanId,
        )
    }

    private fun generateRandomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        val output = StringBuilder(byteCount * 2)
        for (byte in bytes) {
            output.append(BYTE_TO_HEX_TABLE[byte.toInt() and 0xFF])
        }
        return output.toString()
    }

    internal companion object {
        internal const val TRACE_ID_FIELD_KEY = "_trace_id"

        private const val LOWER_HEX_DIGITS = "0123456789abcdef"

        private val BYTE_TO_HEX_TABLE =
            Array(256) { index ->
                val hi = LOWER_HEX_DIGITS[index shr 4]
                val lo = LOWER_HEX_DIGITS[index and 0x0F]
                "$hi$lo"
            }
    }
}

/** Supported trace propagation modes. */
internal enum class TracePropagationMode {
    /** Disable trace header injection. */
    NONE,

    /** W3C Trace Context format via the `traceparent` header. */
    W3C,

    /** Zipkin B3 single-header format via the `b3` header. */
    B3_SINGLE,

    /** Zipkin B3 multiple-headers format via `X-B3-*` headers. */
    B3_MULTI,
    ;

    internal companion object {
        fun fromRuntimeValue(value: String): TracePropagationMode =
            when (value.trim().lowercase()) {
                "none" -> NONE
                "w3c" -> W3C
                "b3-single" -> B3_SINGLE
                "b3-multi" -> B3_MULTI
                else -> NONE
            }
    }
}

internal data class TraceContext(
    val traceId: String,
    val spanId: String,
)
