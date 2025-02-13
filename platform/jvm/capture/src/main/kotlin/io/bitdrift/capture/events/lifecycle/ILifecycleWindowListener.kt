// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.view.Window

/**
 * Emits when a [android.view.Window] is available or removed
 */
interface ILifecycleWindowListener {
    /**
     * Reports when [android.view.Window] is available
     */
    fun onWindowAvailable(window: Window)

    /**
     * Reports when the current window is not around
     */
    fun onWindowRemoved()
}
