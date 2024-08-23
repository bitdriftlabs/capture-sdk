// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import io.bitdrift.capture.replay.ReplayType

internal class DisplayManagers {

    private lateinit var windowManager: WindowManager

    fun init(context: Context) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Suppress("DEPRECATION")
    fun refreshDisplay(): ReplayRect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            ReplayRect(ReplayType.View, bounds.left, bounds.top, bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            ReplayRect(ReplayType.View, 0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }
}
