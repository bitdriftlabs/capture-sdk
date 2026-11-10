// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.graphics.Rect
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil
import kotlin.math.max

/**
 * The surface of this root view's window, read from the hidden `ViewRootImpl.mSurface` field. Below
 * Android 14 a popup has no [android.view.Window] for PixelCopy to copy from, so its surface is the
 * only way to reach what it drew. The field is accessible to apps on those API levels, and the root
 * view's parent is its `ViewRootImpl`.
 *
 * Returns null when the surface cannot be reached for any reason, so that the window is left out of
 * the screenshot rather than failing it.
 */
@Suppress("SwallowedException")
internal fun View.windowSurface(): Surface? =
    try {
        parent?.let { viewRootImpl -> surfaceField.get(viewRootImpl) as? Surface }
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: RuntimeException) {
        // Hidden API enforcement or a ViewRootImpl that has been reshaped.
        null
    }

private val surfaceField by lazy {
    Class
        .forName("android.view.ViewRootImpl")
        .getDeclaredField("mSurface")
        .apply { isAccessible = true }
}

/**
 * The region of this root view's window surface that holds its content: the surface has a margin
 * around the content to draw the window's shadow in. That margin is hidden API, so it is computed
 * the way `WindowManager.LayoutParams.setSurfaceInsets` does: twice the elevation of the root view,
 * or of the background view a `PopupWindow` puts inside it.
 */
internal fun View.surfaceContentRect(): Rect {
    val child = (this as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0)
    val inset = ceil(max(z, child?.z ?: 0f) * 2).toInt()
    return Rect(inset, inset, inset + width, inset + height)
}
