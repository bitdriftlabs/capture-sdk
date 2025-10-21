// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import android.app.Activity
import android.view.View

/**
 * Provides access the Global Window Views, or `null` if no window is available.
 */
interface IWindowManager {
    /**
     * Returns the root view of the current window if available.
     *
     * @return The root view of the current hierarchy, or `null` if not available.
     */
    fun getFirstRootView(): View?

    /**
     * Returns all root views of the current window if available.
     *
     * @return The root views of the current hierarchy, or an empty list if not available.
     */
    fun getAllRootViews(): List<View>

    /**
     * Finds the first valid (non-destroyed) activity from all available root views.
     *
     * For most cases, this returns the currently visible activity.
     *
     * When multiple activities are present (split-screen, PiP, etc), this returns
     * the first valid activity found in the iteration order.
     *
     * @return The first valid [android.app.Activity], or `null` if none found
     */
    fun findFirstValidActivity(): Activity?
}
