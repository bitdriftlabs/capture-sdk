// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View

/**
 * Used for retrieving the view hierarchies
 */
class WindowManager(
    private val errorHandler: ErrorHandler,
) : IWindowManager {
    private val global by lazy(LazyThreadSafetyMode.NONE) {
        //noinspection PrivateApi
        Class.forName("android.view.WindowManagerGlobal")
    }

    override fun getFirstRootView(): View? = getAllRootViews().firstOrNull()

    private val windowManagerGlobal: Any? by lazy(LazyThreadSafetyMode.NONE) {
        global.getDeclaredMethod("getInstance").invoke(null)
    }

    private val getWindowViews by lazy(LazyThreadSafetyMode.NONE) {
        val f = global.getDeclaredField("mViews")
        f.isAccessible = true
        f
    }

    /**
     * Find all DecorViews from [android.view.WindowManagerGlobal]
     * This includes DecorViews from Activities, Dialogs, and other windows.
     */
    @Suppress("KDocUnresolvedReference")
    override fun getAllRootViews(): List<View> {
        // TODO(murki): Consider using the Curtains library for this
        // Use reflection-based approach for all API levels to ensure we capture all windows,
        // including dialogs. WindowInspector may not return all windows in certain scenarios.
        try {
            @Suppress("UNCHECKED_CAST")
            return getWindowViews.get(windowManagerGlobal) as List<View>
        } catch (e: Throwable) {
            errorHandler.handleError("Failed to retrieve windows", e)
            return emptyList()
        }
    }

    override fun findFirstValidActivity(): Activity? =
        getAllRootViews().firstNotNullOfOrNull { view ->
            val activity = view.unwrapToActivity()
            if (activity != null && !activity.isDestroyed) {
                activity
            } else {
                null
            }
        }

    private fun View.unwrapToActivity(): Activity? {
        val visited = mutableSetOf<Context>()
        var current: Context? = WindowSpy.pullWindow(this)?.context

        while (current is ContextWrapper) {
            if (current is Activity) return current
            if (!visited.add(current)) {
                return null
            }
            current = current.baseContext
        }
        return null
    }
}
