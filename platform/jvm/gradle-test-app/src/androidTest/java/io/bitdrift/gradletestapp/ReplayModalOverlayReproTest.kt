// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.util.Log
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * A floating overlay window must not contribute an opaque full-bleed rect: its root and scrim are
 * transparent, and emitting them as [ReplayType.View] paints over every window beneath.
 *
 * Asserts on paint order rather than pixel geometry, so it runs on any device.
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
        // Do not reassign `latch`: ReplayPreviewClient holds the instance created in setUp().
        replayClient.captureScreen()
        // Pump the main looper instead of blocking it; the capture runs there. Bounded because
        // ReplayFilter silently drops a capture identical to the previous one.
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

        // Guards against the assertions below being satisfied by dropping the overlay entirely.
        assertThat(screen.any { it.type == ReplayType.Label && it.y > sh / 2 }).isTrue()
        assertThat(screen.any { it.type == ReplayType.Button && it.y > sh / 2 }).isTrue()

        val firstTransparentFullBleed =
            screen.indexOfFirst { isFullBleed(it) && it.type == ReplayType.TransparentView }
        assertWithMessage(
            "no full-bleed TransparentView found: the overlay window's root was captured as an " +
                "opaque View and will paint over every window beneath it",
        ).that(firstTransparentFullBleed).isAtLeast(0)

        // The sheet's own Surface paints, so it must stay opaque; only the window root and the
        // scrim are transparent. Downgrading everything in the overlay renders the sheet hollow.
        assertWithMessage(
            "the sheet Surface was downgraded to TransparentView, so the sheet renders unfilled",
        ).that(
            screen.any { it.type == ReplayType.View && !isFullBleed(it) && it.y > sh / 2 && it.width >= sw / 2 },
        ).isTrue()

        // Rects are emitted in paint order, bottom window first.
        val lastOpaqueFullBleed =
            screen.indexOfLast { isFullBleed(it) && it.type == ReplayType.View }
        assertWithMessage(
            "an opaque full-bleed View is painted at index $lastOpaqueFullBleed, after the overlay " +
                "window root at $firstTransparentFullBleed; it blanks the frame",
        ).that(lastOpaqueFullBleed).isLessThan(firstTransparentFullBleed)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun interopAndroidViewInsideOverlayIsStillTraversed() {
        composeRule.setContent {
            ModalBottomSheet(onDismissRequest = { }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            TextView(context).apply {
                                text = INTEROP_TEXT
                                layoutParams = ViewGroup.LayoutParams(INTEROP_W, INTEROP_H)
                            }
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val screen = capture()
        dump("interop AndroidView inside ModalBottomSheet", screen)

        assertWithMessage(
            "no Label captured for the interop TextView: the embedded AndroidView was not handed " +
                "to the Android traversal, so its subtree was dropped",
        ).that(screen.any { it.type == ReplayType.Label }).isTrue()
    }

    /**
     * The SDK matches this semantics key by name, because it compiles against a Compose too old to
     * declare it. Fails here if Compose ever renames it, instead of silently hollowing out sheets.
     */
    @Test
    fun shapeSemanticsKeyIsStillNamedShape() {
        assertThat(SemanticsProperties.Shape.name).isEqualTo("Shape")
    }

    private companion object {
        const val INTEROP_TEXT = "Interop TextView"
        const val INTEROP_W = 400
        const val INTEROP_H = 100

        const val CAPTURE_TIMEOUT_MS = 10_000
    }
}
