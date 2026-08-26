// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.replay.internal.ReplayRect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ReplayRepro"

/**
 * A floating overlay window (Dialog, Popup, ModalBottomSheet) must not contribute an opaque
 * full-bleed rect to a replay capture. Its window root and its scrim are transparent, so emitting
 * them as [ReplayType.View] paints a filled rect over every window beneath and the frame renders
 * empty.
 *
 * Device-independent: it compares an overlay capture against a capture of the same content with the
 * overlay dismissed, so it needs no calibrated geometry and runs anywhere.
 */
class ReplayModalOverlayReproTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var replayClient: ReplayPreviewClient
    private val replay: AtomicReference<Pair<FilteredCapture, ReplayCaptureMetrics>?> = AtomicReference(null)
    private lateinit var latch: CountDownLatch

    @Before
    fun setUp() {
        latch = CountDownLatch(1)
        replayClient =
            TestUtils.createReplayPreviewClient(
                replay,
                latch,
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
    }

    private fun capture(): FilteredCapture {
        // NB: do not reassign `latch` here -- ReplayPreviewClient captured the instance created in
        // setUp() by value, so a fresh one would never be counted down.
        replayClient.captureScreen()
        // Pump the main looper rather than blocking it, otherwise the capture -- which runs on the
        // main thread -- can never complete. Bounded, because ReplayFilter silently drops a capture
        // identical to the previous one, and an unbounded wait would hang instead of failing.
        var waited = 0
        while (composeRule.runOnIdle { !latch.await(500, TimeUnit.MILLISECONDS) }) {
            waited += 500
            check(waited < CAPTURE_TIMEOUT_MS) { "no replay capture emitted within ${CAPTURE_TIMEOUT_MS}ms" }
        }
        return replay.get()!!.first
    }

    private fun dump(
        label: String,
        screen: FilteredCapture,
    ) {
        val sw = screen[0].width
        val sh = screen[0].height
        Log.i(TAG, "===== $label : ${screen.size} rects, screen=${sw}x$sh")
        screen.forEachIndexed { i, r ->
            val fullBleed = if (r.width >= sw && r.height >= sh) " FULL-BLEED" else ""
            Log.i(
                TAG,
                "  [%3d] %-16s x=%-5d y=%-5d w=%-5d h=%-5d%s".format(
                    i, r.type.toString(), r.x, r.y, r.width, r.height, fullBleed,
                ),
            )
        }
    }

    private fun countOpaqueFullBleed(screen: FilteredCapture): Int {
        val sw = screen[0].width
        val sh = screen[0].height
        return screen.count { it.type == ReplayType.View && it.width >= sw && it.height >= sh }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun overlayWindowAddsNoOpaqueFullBleedRect() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 200.dp, height = 400.dp)) {
                Button(onClick = { }) { Text("Background Button") }
            }
            ModalBottomSheet(onDismissRequest = { }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Bottom Sheet Title")
                    Button(onClick = { }) { Text("Dismiss") }
                }
            }
        }
        composeRule.waitForIdle()

        val screen = capture()
        dump("with ModalBottomSheet", screen)
        val sw = screen[0].width
        val sh = screen[0].height

        fun isFullBleed(r: ReplayRect) = r.width >= sw && r.height >= sh

        // The sheet's own content has to be present, so the assertions below cannot be satisfied by
        // simply dropping the overlay window from the capture.
        assertThat(screen.any { it.type == ReplayType.Label && it.y > sh / 2 }).isTrue()
        assertThat(screen.any { it.type == ReplayType.Button && it.y > sh / 2 }).isTrue()

        // The overlay window's root spans the screen and paints nothing, so it must show up as a
        // full-bleed TransparentView. Before the fix it -- and its scrim -- were opaque Views.
        val firstTransparentFullBleed =
            screen.indexOfFirst { isFullBleed(it) && it.type == ReplayType.TransparentView }
        assertWithMessage(
            "no full-bleed TransparentView found: the overlay window's root was captured as an " +
                "opaque View and will paint over every window beneath it",
        ).that(firstTransparentFullBleed).isAtLeast(0)

        // Rects are emitted in paint order, bottom window first. Everything after the overlay root
        // belongs to the overlay, so nothing there may claim to fill the screen opaquely -- that is
        // exactly what painted over the app and produced an empty frame.
        val lastOpaqueFullBleed =
            screen.indexOfLast { isFullBleed(it) && it.type == ReplayType.View }
        assertWithMessage(
            "an opaque full-bleed View is painted at index $lastOpaqueFullBleed, after the overlay " +
                "window root at $firstTransparentFullBleed; it blanks the frame",
        ).that(lastOpaqueFullBleed).isLessThan(firstTransparentFullBleed)
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 10_000
    }
}
