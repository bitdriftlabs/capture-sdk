// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

/**
 * Runs the specified task on a background thread
 */
interface IBackgroundThreadHandler {
    /**
     * Runs the specified task off the main thread
     */
    fun runAsync(task: () -> Unit)
}
