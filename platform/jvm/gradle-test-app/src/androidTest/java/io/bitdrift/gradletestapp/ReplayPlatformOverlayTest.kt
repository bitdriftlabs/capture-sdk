// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Dialog
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.FilteredCapture
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PlatformOverlay"

/**
 * Overlay windows built on the platform APIs rather than Compose. Their root is a `DecorView`, which
 * the mapper table resolves to [ReplayType.View]; with no opaque background it must not be reported
 * as occluding, or it paints over every window beneath it.
 */
class ReplayPlatformOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dialog: Dialog? = null

    @Before
    fun setUp() {
        composeRule.setContent {
            Button(onClick = { }, modifier = Modifier.fillMaxSize()) { Text("Beneath the overlay") }
        }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        composeRule.activity.runOnUiThread {
            dialog?.dismiss()
        }
    }

    /** A fresh client each time: [ReplayPreviewClient] captures its latch by value. */
    private fun capture(): FilteredCapture {
        val latch = CountDownLatch(1)
        val holder = AtomicReference<Pair<FilteredCapture, ReplayCaptureMetrics>?>(null)
        val client =
            TestUtils.createReplayPreviewClient(
                holder,
                latch,
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
        client.captureScreen()
        var waited = 0
        while (composeRule.runOnIdle { !latch.await(500, TimeUnit.MILLISECONDS) }) {
            waited += 500
            check(waited < CAPTURE_TIMEOUT_MS) { "no replay capture emitted within ${CAPTURE_TIMEOUT_MS}ms" }
        }
        return holder.get()!!.first
    }

    private fun occludingRects(screen: FilteredCapture): List<Int> {
        val sw = screen[0].width
        val sh = screen[0].height
        return screen.withIndex()
            .filter { (_, r) -> r.type == ReplayType.View && r.width >= sw && r.height * 2 >= sh }
            .map { it.index }
    }

    private fun dump(label: String, screen: FilteredCapture) {
        Log.i(TAG, "===== $label : ${screen.size} rects")
        screen.forEachIndexed { i, r ->
            Log.i(TAG, "  [%2d] %-16s %d,%d %dx%d".format(i, r.type.toString(), r.x, r.y, r.width, r.height))
        }
    }

    private fun assertOverlayAddsNoOccluder(label: String, showOverlay: () -> Unit) {
        val before = capture()
        dump("$label - before", before)
        val baseline = occludingRects(before).size

        composeRule.activity.runOnUiThread { showOverlay() }
        composeRule.waitForIdle()

        val after = capture()
        dump("$label - after", after)
        val withOverlay = occludingRects(after).size

        assertWithMessage(
            "$label added ${withOverlay - baseline} occluding rect(s) at indices " +
                "${occludingRects(after)}; its window root paints over everything beneath",
        ).that(withOverlay).isAtMost(baseline)
    }

    @Test
    fun platformDialogWindowRootDoesNotOcclude() {
        assertOverlayAddsNoOccluder("platform Dialog") {
            dialog =
                Dialog(composeRule.activity).apply {
                    setContentView(View(context))
                    show()
                }
        }
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 10_000
    }
}
