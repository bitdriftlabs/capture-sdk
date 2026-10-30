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
 * Provides access the Global Window Views
 */
interface IWindowManager {
    /**
     * Returns the top-most root view that is shown and has a size: the window a screenshot of the
     * app should capture.
     *
     * Root views are ordered bottom to top, so this is the last visible one. An activity stacked
     * under another one in the same task keeps its root view, hidden, lower in that order; when a
     * dialog or popup is open, its window is the top-most one and is what gets returned.
     *
     * @return The top-most visible root view, or `null` if no window is currently shown.
     */
    fun getTopMostVisibleRootView(): View?

    /**
     * Returns all root views across all windows of the application.
     *
     * This includes the main application window, dialogs, popups, and any other
     * system-level windows owned by the app. The views are typically ordered
     * from bottom to top in the Z-order (the first element is the main window,
     * and the last is the top-most window like a dialog).
     *
     * @return A list of all root views for the application, or an empty list if none are available.
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
