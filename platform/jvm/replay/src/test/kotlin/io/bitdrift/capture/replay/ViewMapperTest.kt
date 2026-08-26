// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.ScannableView
import io.bitdrift.capture.replay.internal.mappers.ViewMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk is pinned because Robolectric 4.13 does not support this module's compileSdk.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewMapperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Draws itself in `onDraw`, so it has no background drawable to infer opacity from. */
    private class CustomDrawnView(
        context: Context,
    ) : View(context)

    private fun map(
        view: View,
        categorizers: Map<ReplayType, List<String>>? = null,
    ): List<ReplayRect> =
        ViewMapper(SessionReplayConfiguration(categorizers))
            .mapView(ScannableView.AndroidView(view, skipReplayComposeViews = true))

    /** A class the default mapper table resolves to [ReplayType.View]. */
    private fun builtInGenericView(background: ColorDrawable? = null): View =
        View(context).apply {
            this.background = background
            layout(0, 0, 100, 100)
        }

    private fun customView(): View =
        CustomDrawnView(context).apply {
            layout(0, 0, 100, 100)
        }

    @Test
    fun externallyCategorizedViewKeepsRequestedTypeWithoutBackground() {
        val rects = map(customView(), mapOf(ReplayType.View to listOf("CustomDrawnView")))

        assertThat(rects).containsExactly(ReplayRect(ReplayType.View, 0, 0, 100, 100))
    }

    @Test
    fun externallyCategorizedNonGenericTypeIsUntouched() {
        val rects = map(customView(), mapOf(ReplayType.Map to listOf("CustomDrawnView")))

        assertThat(rects).containsExactly(ReplayRect(ReplayType.Map, 0, 0, 100, 100))
    }

    @Test
    fun builtInGenericViewWithoutBackgroundIsTransparent() {
        val rects = map(builtInGenericView(), categorizers = null)

        assertThat(rects).containsExactly(ReplayRect(ReplayType.TransparentView, 0, 0, 100, 100))
    }

    @Test
    fun builtInGenericViewWithOpaqueBackgroundStaysOpaque() {
        val rects = map(builtInGenericView(ColorDrawable(Color.RED)), categorizers = null)

        assertThat(rects).containsExactly(ReplayRect(ReplayType.View, 0, 0, 100, 100))
    }

    @Test
    fun builtInGenericViewWithTransparentBackgroundIsTransparent() {
        val rects = map(builtInGenericView(ColorDrawable(Color.TRANSPARENT)), categorizers = null)

        assertThat(rects).containsExactly(ReplayRect(ReplayType.TransparentView, 0, 0, 100, 100))
    }
}
