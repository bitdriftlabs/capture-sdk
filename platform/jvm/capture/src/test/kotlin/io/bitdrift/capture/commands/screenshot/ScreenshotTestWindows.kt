// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.fakes.FakeWindowManager
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * Builds real, attached windows for the screenshot tests. Each is a flat colour at a known place, so
 * a test can tell which windows made it into a screenshot, and in what order, by sampling pixels.
 */
internal class ScreenshotTestWindows(
    val width: Int = 200,
    val height: Int = 400,
) {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    // Robolectric lays every window out again whenever the main looper idles, so colours and places
    // are only applied once all the windows exist, by [showing].
    private val placements = mutableListOf<() -> Unit>()

    /** An [IWindowManager] whose windows are [roots], bottom to top, in the colours and places they were built with. */
    fun showing(vararg roots: View): IWindowManager {
        shadowOf(Looper.getMainLooper()).idle()
        placements.forEach { it() }
        return FakeWindowManager(rootViews = roots.toList())
    }

    /** Makes [window] report that its window is not visible, like one of an activity that is stopped. */
    fun hide(window: View) {
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(window, "mAttachInfo"), "mWindowVisibility", View.GONE)
    }

    private fun View.placed(
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): View =
        apply {
            placements +=
                {
                    // Nothing on top of the colour: the platform's content layouts have a background of their own.
                    (this as? ViewGroup)?.removeAllViews()
                    setBackgroundColor(color)
                    // A root view is laid out at the origin of its window; where the window is on
                    // the screen is what getLocationOnScreen adds, from the attach info.
                    layout(0, 0, right - left, bottom - top)
                    val attachInfo = ReflectionHelpers.getField<Any>(this, "mAttachInfo")
                    ReflectionHelpers.setField(attachInfo, "mWindowLeft", left)
                    ReflectionHelpers.setField(attachInfo, "mWindowTop", top)
                }
        }

    /** The activity's window, filling the [width] by [height] screen with [color]. */
    fun activityWindow(color: Int = Color.RED): View = activity.window.decorView.placed(color, 0, 0, width, height)

    /** A dialog window of [color] covering [left], [top], [right], [bottom], with the platform's dim behind it. */
    fun dialogWindow(
        color: Int = Color.BLUE,
        left: Int = 50,
        top: Int = 100,
        right: Int = 150,
        bottom: Int = 300,
        dimAmount: Float = 0.5f,
    ): View {
        val dialog =
            Dialog(activity).apply {
                window!!.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window!!.setDimAmount(dimAmount)
                show()
            }
        return dialog.window!!.decorView.placed(color, left, top, right, bottom)
    }

    /**
     * A popup sub-window of [color] covering [left], [top], [right], [bottom], attached to
     * [parent]'s window like a `PopupWindow` is, or to [token] when given.
     */
    fun popupWindow(
        parent: View,
        color: Int = Color.GREEN,
        left: Int = 20,
        top: Int = 200,
        right: Int = 120,
        bottom: Int = 350,
        token: IBinder? = parent.windowToken,
    ): View {
        val popup = View(activity)
        val params =
            WindowManager
                .LayoutParams(
                    right - left,
                    bottom - top,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply { this.token = token }
        activity.windowManager.addView(popup, params)
        return popup.placed(color, left, top, right, bottom)
    }
}
