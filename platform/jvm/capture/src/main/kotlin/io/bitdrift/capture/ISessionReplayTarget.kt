// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Responsible for emitting session replay screen logs and command screenshots.
 */
interface ISessionReplayTarget {
    /**
     * Called to indicate that the target is supposed to prepare and emit a session replay screen log.
     */
    fun captureScreen()

    /** Requests a screenshot whose JPEG bytes are returned to the remote command executor. */
    fun captureDeviceCommandScreenshot(requestId: Long)
}

/**
 * A no-op implementation of SessionReplayTarget used when session replay is disabled.
 */
internal class NoopSessionReplayTarget : ISessionReplayTarget {
    override fun captureScreen() {
        // no-op
    }

    override fun captureDeviceCommandScreenshot(requestId: Long) {
        CaptureJniLibrary.completeDeviceCommandScreenshot(requestId, null)
    }
}
