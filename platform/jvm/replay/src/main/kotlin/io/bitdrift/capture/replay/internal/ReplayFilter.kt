// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

/**
 * The list of captured elements after filtering
 */
typealias FilteredCapture = List<ReplayRect>

internal class ReplayFilter {
    private var previousCapture: FilteredCapture? = null

    fun filter(capture: FilteredCapture): FilteredCapture? {
        // This capture is identical to the previous one, or is empty, filter it out
        // One interesting case when capture is empty is when the application is backgrounded.
        // Note: With the new ReplayParser, capture will always contain at least the screen bounds
        // if correctly parsed, but we keep the isEmpty check for robustness.
        return if (capture == previousCapture || capture.isEmpty()) {
            null
        } else {
            previousCapture = capture
            capture
        }
    }
}
