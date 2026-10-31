// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.common

import android.app.Activity
import android.view.View
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.bitdrift.capture.ErrorHandler as CaptureErrorHandler

/** Exercises [WindowManager] against real Robolectric activities, whose windows it discovers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class WindowManagerTest {
    private val windowManager = WindowManager(CaptureErrorHandler())

    @Test
    fun topMostVisibleRootViewIsTheLastShownWindowWithASize() {
        val bottom =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
                .get()
                .window.decorView
                .sized()
        val top =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
                .get()
                .window.decorView
                .sized()
        assertThat(windowManager.getAllRootViews()).containsExactly(bottom, top)

        assertThat(windowManager.getTopMostVisibleRootView()).isSameAs(top)

        // A hidden window on top, as a stopped activity's would be, is skipped for the one below it.
        top.visibility = View.INVISIBLE
        assertThat(windowManager.getTopMostVisibleRootView()).isSameAs(bottom)

        // A window without a size cannot be captured.
        bottom.layout(0, 0, 0, 0)
        assertThat(windowManager.getTopMostVisibleRootView()).isNull()
    }

    private fun View.sized(): View = apply { layout(0, 0, 1080, 2400) }
}
