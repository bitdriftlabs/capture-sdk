// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import java.security.SecureRandom

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
internal enum class TracePropagationMode(
    /** Stable text representation safe for serialization/logging. */
    val value: String,
) {
    /** Disable trace header injection. */
    NONE("none"),

    /** W3C Trace Context format via the `traceparent` header. */
    W3C("w3c"),

    /** Zipkin B3 single-header format via the `b3` header. */
    B3_SINGLE("b3-single"),

    /** Zipkin B3 multiple-headers format via `X-B3-*` headers. */
    B3_MULTI("b3-multi"),
    ;

    internal companion object {
        fun fromRuntimeValue(configValue: String): TracePropagationMode =
            when (configValue.trim().lowercase()) {
                NONE.value -> NONE
                W3C.value -> W3C
                B3_SINGLE.value -> B3_SINGLE
                B3_MULTI.value -> B3_MULTI
                else -> NONE
            }
    }
}

internal data class TraceContext(
    val traceId: String,
    val spanId: String,
)
