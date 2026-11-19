// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * The SDK's initialization state.
 */
enum class InitializationState {
    /** The SDK has not been started yet. */
    NOT_STARTED,

    /** The SDK library has been loaded but log processing has not yet begun. */
    LOADED,

    /** The SDK is fully running and processing logs. */
    RUNNING,

    /** The SDK has been force-disabled by the server (e.g., authentication failure). */
    DISABLED,
}

/**
 * A point-in-time snapshot of the SDK's operational status.
 *
 * @property initializationState The current initialization state of the SDK.
 * @property lastHandshakeTimeMs The wall-clock time (epoch millis) of the last successful
 *           handshake, or `null` if no handshake has occurred.
 * @property lastConfigDeliveryTimeMs The wall-clock time (epoch millis) of the last successful
 *           config delivery from the backend, or `null` if none has occurred.
 */
data class SdkStatus(
    val initializationState: InitializationState,
    val lastHandshakeTimeMs: Long?,
    val lastConfigDeliveryTimeMs: Long?,
) {
    /**
     * JNI constructor that accepts the initialization state as an integer ordinal
     * and timestamps where -1 means not available.
     */
    internal constructor(
        initializationStateOrdinal: Int,
        lastHandshakeTimeMs: Long,
        lastConfigDeliveryTimeMs: Long,
    ) : this(
        initializationState = InitializationState.entries[initializationStateOrdinal],
        lastHandshakeTimeMs = lastHandshakeTimeMs.takeIf { it >= 0 },
        lastConfigDeliveryTimeMs = lastConfigDeliveryTimeMs.takeIf { it >= 0 },
    )
}
