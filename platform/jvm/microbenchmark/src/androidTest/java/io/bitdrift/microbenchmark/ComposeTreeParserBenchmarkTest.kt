// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.microbenchmark

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.window.Dialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.bitdrift.capture.replay.internal.compose.ComposeTreeParser
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BRANCHING = 3
private const val DEPTH = 5

/** ~3^5 nodes of layout containers wrapping text, approximating a dense screen. */
@Composable
private fun DeepTree(depth: Int) {
    if (depth == 0) {
        Text("leaf")
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(BRANCHING) {
            Row { Box { DeepTree(depth - 1) } }
        }
    }
}

/**
 * Cost of [ComposeTreeParser.parse] on a Compose tree.
 *
 * The overlay pre-pass walks the semantics tree once before mapping it, so the case to watch is a
 * screen with no Dialog or Popup: nothing short-circuits and the whole tree is visited twice.
 */
@RunWith(AndroidJUnit4::class)
class ComposeTreeParserBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    /**
     * Hosts [content] and hands back its AndroidComposeView. The measurement deliberately runs
     * outside `onActivity`: that callback holds the main thread, and the benchmark's IsolationActivity
     * cannot launch between tests while it is blocked.
     */
    private fun withParsedTree(
        content: @Composable () -> Unit,
        block: (View) -> Unit,
    ) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var androidComposeView: View? = null
            scenario.onActivity { activity ->
                val composeView = ComposeView(activity).apply { setContent { content() } }
                activity.setContentView(composeView)
                androidComposeView = (composeView as ViewGroup).getChildAt(0)
            }
            val view = androidComposeView
            Assert.assertNotNull("no AndroidComposeView was created", view)
            block(view!!)
        }
    }

    /** No overlay: the pre-pass scans every node and finds nothing. Worst case. */
    @Test
    fun parseTreeWithoutOverlay() {
        withParsedTree({ DeepTree(DEPTH) }) { view ->
            benchmarkRule.measureRepeated { ComposeTreeParser.parse(view) }
        }
    }

    /** Overlay present: the pre-pass short-circuits as soon as it reaches the marker. */
    @Test
    fun parseTreeWithOverlay() {
        withParsedTree({
            Dialog(onDismissRequest = {}) { Text("overlay") }
            DeepTree(DEPTH)
        }) { view ->
            benchmarkRule.measureRepeated { ComposeTreeParser.parse(view) }
        }
    }
}
