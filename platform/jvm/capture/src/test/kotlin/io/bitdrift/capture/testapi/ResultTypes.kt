// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.testapi

import kotlin.time.Duration

/**
 * Result of waiting for a handshake.
 */
sealed interface HandshakeResult {
    data object Success : HandshakeResult

    data class MetadataMismatch(
        val expected: Map<String, String>,
        val actual: Map<String, String>,
        val differences: List<String>,
    ) : HandshakeResult

    data class Timeout(
        val duration: Duration,
    ) : HandshakeResult
}

/**
 * Result of waiting for a stream to close.
 */
sealed interface StreamCloseResult {
    data object Closed : StreamCloseResult

    data object StillOpen : StreamCloseResult

    data class Timeout(
        val duration: Duration,
    ) : StreamCloseResult
}

/**
 * Result of waiting for a configuration acknowledgment.
 */
sealed interface ConfigurationAckResult {
    data object Success : ConfigurationAckResult

    data class Timeout(
        val duration: Duration,
    ) : ConfigurationAckResult
}
