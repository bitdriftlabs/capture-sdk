// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.testapi

import kotlin.time.Duration

/**
 * Type-safe wrapper for server port to prevent mixing up integers.
 */
@JvmInline
value class ServerPort(
    val value: Int,
) {
    init {
        require(value > 0) { "Port must be positive, got $value" }
    }
}

/**
 * Type-safe wrapper for stream IDs to prevent mixing up integers.
 */
@JvmInline
value class StreamId(
    val value: Int,
) {
    companion object {
        val INVALID = StreamId(-1)
    }

    val isValid: Boolean get() = value != -1
}

/**
 * Configuration for the test API server.
 */
data class ServerConfig(
    val pingInterval: Duration = Duration.INFINITE,
) {
    companion object {
        fun build(block: Builder.() -> Unit = {}): ServerConfig = Builder().apply(block).build()
    }

    class Builder {
        var pingInterval: Duration = Duration.INFINITE

        fun build() = ServerConfig(pingInterval)
    }
}
