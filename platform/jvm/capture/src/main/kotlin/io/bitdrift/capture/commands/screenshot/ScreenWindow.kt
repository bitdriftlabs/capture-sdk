// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import io.bitdrift.capture.common.IWindowManager

/**
 * A window of the app that is on screen: its root view and where its top left corner sits in screen
 * coordinates.
 */
internal class ScreenWindow(
    val root: View,
    val left: Int,
    val top: Int,
) {
    val width: Int get() = root.width
    val height: Int get() = root.height

    /** Null for a root view the platform did not attach window attributes to. */
    val params: WindowManager.LayoutParams? get() = root.layoutParams as? WindowManager.LayoutParams

    val isSubWindow: Boolean
        get() = params?.type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW

    /**
     * Whether every pixel of the window is opaque: its surface has no alpha channel, or its root view
     * draws an opaque background under everything else. Apps often ask for an RGBA_8888 surface for
     * an opaque activity, so the format alone would rarely tell.
     */
    val isOpaque: Boolean
        get() = params?.format?.let { !PixelFormat.formatHasAlpha(it) } == true || root.isOpaque

    /** Whether the window is marked [WindowManager.LayoutParams.FLAG_SECURE], so must never be copied. */
    val isSecure: Boolean
        get() = (params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0

    /** Whether the window covers exactly [bounds], in screen coordinates. */
    fun fills(bounds: Rect): Boolean = left == bounds.left && top == bounds.top && width == bounds.width() && height == bounds.height()
}

/**
 * The windows a screenshot is made of, bottom to top: every root view that is attached, visible and
 * has a size, in the order they are stacked in (see [inStackingOrder]).
 */
internal fun IWindowManager.visibleWindowsInStackingOrder(): List<ScreenWindow> =
    getAllRootViews()
        .filter { it.isAttachedToWindow && it.windowVisibility == View.VISIBLE && it.width > 0 && it.height > 0 }
        .map { root ->
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            ScreenWindow(root, location[0], location[1])
        }.inStackingOrder()

/**
 * Sorts windows from the order they were added in to the order they are stacked in. A sub-window,
 * such as a popup, is drawn right above the window it is attached to, so under any dialog that was
 * added after that window.
 */
internal fun List<ScreenWindow>.inStackingOrder(): List<ScreenWindow> {
    val (subWindows, windows) = partition { it.isSubWindow }
    val placed = mutableSetOf<ScreenWindow>()
    val ordered = mutableListOf<ScreenWindow>()
    for (window in windows) {
        ordered += window
        val token = window.root.windowToken ?: continue
        for (subWindow in subWindows) {
            if (subWindow.params?.token == token) {
                ordered += subWindow
                placed += subWindow
            }
        }
    }
    // Attached to a window that is not in the list: on top is the best guess.
    return ordered + subWindows.filterNot { it in placed }
}
