// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Responsible for emitting resource utilization logs in response to provided ticks.
 */
internal interface IResourceUtilizationTarget {
    /**
     * Called to indicate that the target is supposed to prepare and emit a resource utilization log.
     */
    fun tick()
}
