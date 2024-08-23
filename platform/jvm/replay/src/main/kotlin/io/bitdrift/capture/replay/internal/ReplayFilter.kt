// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.replay.ReplayType

/**
 * The list of captured elements after filtering
 */
typealias FilteredCapture = List<ReplayRect>

internal class ReplayFilter {
    private var previousCapture: FilteredCapture? = null

    fun filter(capture: Capture): FilteredCapture? {
        val filteredCapture = mutableListOf<ReplayRect>()
        for (window in capture) {
            for (view in window) {
                if (view.type != ReplayType.Ignore) {
                    // TODO(murki): Collapse this redundant functionality with ViewMapper
                    filteredCapture.add(view)
                }
            }
        }

        // This capture is identical to the previous one, or is empty, filter it out
        return if (filteredCapture == previousCapture || filteredCapture.isEmpty()) {
            null
        } else {
            previousCapture = filteredCapture
            filteredCapture
        }
    }
}
