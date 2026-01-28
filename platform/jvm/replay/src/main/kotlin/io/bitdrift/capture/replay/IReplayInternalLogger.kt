// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

/**
 * Forwards messages as type Internal to the bitdrift Logger
 *
 */
interface IReplayInternalLogger {
    /**
     * Forwards a verbose message internally to the SDK
     */
    fun logVerboseInternal(message: String)

    /**
     * Forwards a debug message internally to the SDK
     */
    fun logDebugInternal(message: String)

    /**
     * Forwards an error message internally to the SDK
     */
    fun logErrorInternal(
        message: String,
        e: Throwable? = null,
    )
}
