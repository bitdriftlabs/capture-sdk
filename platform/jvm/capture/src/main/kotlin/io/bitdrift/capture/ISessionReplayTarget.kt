// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Responsible for emitting session replay screen and screenshot logs.
 */
interface ISessionReplayTarget {
    /**
     * Called to indicate that the target is supposed to prepare and emit a session replay screen log.
     */
    fun captureScreen()

    /**
     * Called to indicate that the target should prepare and emit a session replay screenshot log.
     * The Rust logger does not request another screenshot until it receives the previously
     * requested one. This mechanism is designed to ensure that there are no situations where
     * the Rust logger requests screenshots at a rate faster than the platform layer can handle.
     */
    fun captureScreenshot()
}

/**
 * A no-op implementation of SessionReplayTarget used when session replay is disabled.
 */
internal class NoopSessionReplayTarget : ISessionReplayTarget {
    override fun captureScreen() {
        // no-op
    }

    override fun captureScreenshot() {
        // no-op
    }
}
