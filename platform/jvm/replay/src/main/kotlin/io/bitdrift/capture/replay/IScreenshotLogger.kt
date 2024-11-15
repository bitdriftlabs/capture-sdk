// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

/**
 * Screenshots will be received through this interface
 */
interface IScreenshotLogger : IInternalLogger {

    /**
     * Called when a screenshot is received
     * @param compressedScreen The compressed screenshot in binary format
     * @param metrics Metrics about the screenshot and compression process
     */
    fun onScreenshotCaptured(compressedScreen: ByteArray, metrics: ScreenshotCaptureMetrics)
}
