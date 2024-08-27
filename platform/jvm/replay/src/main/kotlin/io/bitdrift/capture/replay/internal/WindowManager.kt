// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.replay.L

// Used for retrieving the view hierarchies
internal class WindowManager(private val errorHandler: ErrorHandler) {

    private var tryWindowInspector = true

    private val global by lazy(LazyThreadSafetyMode.NONE) {
        //noinspection PrivateApi
        Class.forName("android.view.WindowManagerGlobal")
    }

    private val windowManagerGlobal: Any by lazy(LazyThreadSafetyMode.NONE) {
        global.getDeclaredMethod("getInstance").invoke(null)
    }

    private val getWindowViews by lazy(LazyThreadSafetyMode.NONE) {
        val f = global.getDeclaredField("mViews")
        f.isAccessible = true
        f
    }

    /**
     * Find all DecorViews from [android.view.WindowManagerGlobal]
     */
    @Suppress("KDocUnresolvedReference", "SwallowedException")
    fun findRootViews(): List<View> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && tryWindowInspector) {
            try {
                return WindowInspector.getGlobalWindowViews()
            } catch (e: Throwable) {
                tryWindowInspector = false
            }
        }
        try {
            @Suppress("UNCHECKED_CAST")
            return getWindowViews.get(windowManagerGlobal) as List<View>
        } catch (e: Throwable) {
            L.e(e, "Failed to retrieve windows")
            errorHandler.handleError("Failed to retrieve windows", e)
            return emptyList()
        }
    }
}
