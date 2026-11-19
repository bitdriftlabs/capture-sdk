// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import io.bitdrift.capture.replay.internal.FilteredCapture

/**
 * Screen captures will be received through this interface
 */
interface IReplayLogger : IReplayInternalLogger {
    /**
     * Called when a screen capture is received
     * @param encodedScreen The encoded screen capture in binary format
     * @param screen The list of captured elements after filtering
     * @param metrics Metrics about the screen capture
     */
    fun onScreenCaptured(
        encodedScreen: ByteArray,
        screen: FilteredCapture,
        metrics: ReplayCaptureMetrics,
    )
}
