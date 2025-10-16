// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import android.view.View
import android.view.Window

/**
 * Provides access the Global Window Views, or `null` if no window is available.
 */
interface IWindowManager {
    /**
     * Returns the current [Window] if available.
     *
     * @return The current [Window], or `null` if no window is available.
     */
    fun getCurrentWindow(): Window?

    /**
     * Returns the root view of the current window if available.
     *
     * @return The root view of the current hierarchy, or `null` if not available.
     */
    fun getFirstRootView(): View?

    fun getLastRootView(): View?

    /**
     * Returns all root views of the current window if available.
     *
     * @return The root views of the current hierarchy, or an empty list if not available.
     */
    fun getAllRootViews(): List<View>
}
