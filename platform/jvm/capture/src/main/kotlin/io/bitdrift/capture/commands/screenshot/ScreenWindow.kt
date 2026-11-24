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
    val width: Int = root.width
    val height: Int = root.height

    /** Null for a root view the platform did not attach window attributes to. */
    val params: WindowManager.LayoutParams? get() = root.layoutParams as? WindowManager.LayoutParams

    /** Opacity applied by the system compositor, outside the window surface. */
    val alpha: Float get() = (params?.alpha ?: 1f).coerceIn(0f, 1f)

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
        .filter { it.isAttachedToWindow && it.isShown && it.windowVisibility == View.VISIBLE && it.width > 0 && it.height > 0 }
        .map { root ->
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            ScreenWindow(root, location[0], location[1])
        }.inStackingOrder()

/**
 * Keeps top-level windows in discovery order and places sub-windows next to their parent, ordered
 * by platform layer: media below, panels and sub-panels above. This approximates stacking for app
 * activities, dialogs and popups; it does not query the system compositor's global Z-order.
 */
internal fun List<ScreenWindow>.inStackingOrder(): List<ScreenWindow> {
    val (subWindows, windows) = partition { it.isSubWindow }
    val placed = mutableSetOf<ScreenWindow>()
    val ordered = mutableListOf<ScreenWindow>()
    for (window in windows) {
        val token = window.root.windowToken
        val children = subWindows.filter { token != null && it.params?.token == token }.sortedBy { it.subLayer }
        ordered += children.filter { it.subLayer < 0 }
        ordered += window
        ordered += children.filter { it.subLayer >= 0 }
        placed += children
    }
    // Attached to a window that is not in the list: on top is the best guess.
    return ordered + subWindows.filterNot { it in placed }
}

/** Includes platform-created sub-window types omitted from the public WindowType IntDef. */
@Suppress("SwitchIntDef")
private val ScreenWindow.subLayer: Int
    get() =
        when (params?.type) {
            WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA -> -2
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL -> 2
            TYPE_APPLICATION_MEDIA_OVERLAY -> -1
            TYPE_APPLICATION_ABOVE_SUB_PANEL -> 3
            else -> 1
        }

// Hidden platform window types, stable since before the minimum supported screenshot API.
private const val TYPE_APPLICATION_MEDIA_OVERLAY = WindowManager.LayoutParams.FIRST_SUB_WINDOW + 4
private const val TYPE_APPLICATION_ABOVE_SUB_PANEL = WindowManager.LayoutParams.FIRST_SUB_WINDOW + 5
