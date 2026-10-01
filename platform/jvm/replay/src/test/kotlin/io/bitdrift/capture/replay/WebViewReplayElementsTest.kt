// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.replay.internal.ReplayRect
import io.bitdrift.capture.replay.internal.webViewReplayRects
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class WebViewReplayElementsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val webView =
        View(context).apply {
            layout(0, 0, 400, 800)
        }

    @Test
    fun scalesCssPixelsToViewPixelsAndClipsToViewBounds() {
        val snapshot =
            WebViewReplaySnapshot(
                viewportWidth = 200,
                viewportHeight = 400,
                elements =
                    intArrayOf(
                        ReplayType.Button.typeValue,
                        10,
                        20,
                        50,
                        10,
                        ReplayType.Label.typeValue,
                        190,
                        390,
                        40,
                        40,
                        ReplayType.Image.typeValue,
                        0,
                        500,
                        10,
                        10,
                        ReplayType.Map.typeValue,
                        0,
                        0,
                        10,
                        10,
                    ),
            )

        Assert.assertEquals(
            listOf(
                ReplayRect(ReplayType.Button, 20, 40, 100, 20),
                ReplayRect(ReplayType.Label, 380, 780, 20, 20),
            ),
            webViewReplayRects(webView, snapshot),
        )
    }

    @Test
    fun ignoresTrailingIncompleteElement() {
        val snapshot =
            WebViewReplaySnapshot(
                viewportWidth = 400,
                viewportHeight = 800,
                elements = intArrayOf(ReplayType.View.typeValue, 0, 0, 400, 100, ReplayType.Label.typeValue, 0),
            )

        Assert.assertEquals(
            listOf(ReplayRect(ReplayType.View, 0, 0, 400, 100)),
            webViewReplayRects(webView, snapshot),
        )
    }

    @Test
    fun emptyViewportProducesNoRects() {
        val snapshot = WebViewReplaySnapshot(0, 0, intArrayOf(ReplayType.View.typeValue, 0, 0, 10, 10))

        Assert.assertEquals(emptyList<ReplayRect>(), webViewReplayRects(webView, snapshot))
    }
}
