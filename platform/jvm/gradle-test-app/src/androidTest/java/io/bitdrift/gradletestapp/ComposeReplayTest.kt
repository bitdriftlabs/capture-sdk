// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.compose.CaptureModifier.captureIgnore
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.replay.internal.ReplayRect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun ComposeContentTestRule.setContentWithExplicitRoot(content: @Composable () -> Unit) {
    setContent {
        Box {
            content()
        }
    }
}

// These tests run via github actions using a Nexus 6 API 23 which has a screen size of 1440 x 2560
// emulator -avd Nexus_6_API_23 \
// -no-window -accel on -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
class ComposeReplayTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var replayClient: ReplayPreviewClient
    private val replay: AtomicReference<Pair<FilteredCapture, ReplayCaptureMetrics>?> = AtomicReference(null)
    private lateinit var latch: CountDownLatch

    @Before
    fun setUp() {
        latch = CountDownLatch(1)
        // We defer to ReplayPreviewClient to manage the screen capture logic
        replayClient = TestUtils.createReplayPreviewClient(replay, latch, InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun verifyReplayScreen(viewCount: Int = 3): FilteredCapture {
        replayClient.captureScreen()

        // If we do an unconditional wait on the latch we may end up preventing the replay invocation
        // to run on the main thread, which ends up deadlocking the tests.
        while (composeRule.runOnIdle {
                !latch.await(1000, TimeUnit.MILLISECONDS)
            }
        ) {
            continue
        }

        val (screen, metrics) = replay.get()!!

        assertThat(metrics.errorViewCount).isEqualTo(0)
        assertThat(metrics.exceptionCausingViewCount).isEqualTo(0)
        // Mostly ignoring view count since it's not a good indicator of anything
        // We add 1 since the screen view is not included in this counter.
//        assertThat(screen.size).isEqualTo(viewCount)
        assertThat(screen.size).isGreaterThan(1)

        // The first frame is always going to be the screen size. We always use a Nexus 6 in test so
        // this should always be the size.
        assertThat(screen[0]).isEqualTo(ReplayRect(ReplayType.View, 0, 0, 1440, 2560))

        return screen
    }

    // TODO(murki): Add test to validate we traverse the tree using 'preorder'

    @Test
    fun capturesSizeOfInnerView() {
        composeRule.setContentWithExplicitRoot {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(30.toDp(), 40.toDp())
                }
            Box(modifier = Modifier.size(width, height))
        }

        // y=84 due to View { id:statusBarBackground, 1440×84px } pushing things down
        assertThat(verifyReplayScreen(viewCount = 7)).contains(
            ReplayRect(
                ReplayType.View,
                0,
                84,
                30,
                40,
            ),
        )
    }

    @Test
    fun zeroSizedViewsAreIgnored() {
        composeRule.setContentWithExplicitRoot {
            Box(Modifier)
        }

        verifyReplayScreen(viewCount = 4).forEach {
            assertThat(it.height).isGreaterThan(0)
            assertThat(it.width).isGreaterThan(0)
        }
    }

    @Test
    fun basicText() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.fillMaxWidth()) {
                BasicText("Baguette Avec Fromage")
                BasicText("short")
            }
        }

        val capture = verifyReplayScreen(viewCount = 7)
        // different widths reflect text length
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 0, 84, 522, 57))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 0, 141, 114, 57))
    }

    @Test
    fun basicTextWithEmptyString() {
        composeRule.setContentWithExplicitRoot {
            BasicText("")
        }

        // No additional views are captured.
        verifyReplayScreen(viewCount = 4)
    }

    @Test
    fun text() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.fillMaxWidth()) {
                Text("Baguette Avec Fromage")
                Text("short")
            }
        }

        val capture = verifyReplayScreen(viewCount = 8)
        // different widths reflect text length
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 0, 84, 522, 57))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 0, 141, 114, 57))
    }

    @Test
    fun clickableText() {
        composeRule.setContentWithExplicitRoot {
            ClickableText(text = AnnotatedString("Baguette Avec Fromage"), onClick = {})
        }

        assertThat(verifyReplayScreen(viewCount = 8)).contains(
            ReplayRect(
                ReplayType.Label,
                0,
                84,
                522,
                57,
            ),
        )
    }

    @Test
    fun textField() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.wrapContentWidth()) {
                TextField("", onValueChange = {}, label = {}, modifier = Modifier.wrapContentWidth())
                TextField("Baguette Avec Fromage", onValueChange = {}, modifier = Modifier.wrapContentWidth())
            }
        }

        val capture = verifyReplayScreen(viewCount = 9)
        // TODO(snowp): We currently aren't able to reflect the true text size within the text field.
        assertThat(capture).contains(ReplayRect(ReplayType.TextInput, 0, 84, 980, 196))
        assertThat(capture).contains(ReplayRect(ReplayType.TextInput, 0, 280, 980, 196))
    }

    @Test
    fun checkbox() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.wrapContentWidth()) {
                Checkbox(checked = false, onCheckedChange = {})
                Checkbox(checked = true, onCheckedChange = {})
            }
        }

        val capture = verifyReplayScreen(viewCount = 7)
        assertThat(capture).contains(ReplayRect(ReplayType.SwitchOff, 42, 126, 84, 84))
        assertThat(capture).contains(ReplayRect(ReplayType.SwitchOn, 42, 294, 84, 84))
    }

    @Test
    fun icon() {
        composeRule.setContentWithExplicitRoot {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Content Description")
        }

        assertThat(verifyReplayScreen(viewCount = 9)).contains(
            ReplayRect(
                ReplayType.Image,
                0,
                84,
                84,
                84,
            ),
        )
    }

    @Test
    fun image() {
        composeRule.setContentWithExplicitRoot {
            Image(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Content Description")
        }

        assertThat(verifyReplayScreen(viewCount = 8)).contains(
            ReplayRect(
                ReplayType.Image,
                0,
                84,
                84,
                84,
            ),
        )
    }

    @Test
    fun button() {
        composeRule.setContentWithExplicitRoot {
            Button(onClick = {}) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Content Description")
            }
        }

        assertThat(verifyReplayScreen(viewCount = 13)).contains(
            ReplayRect(
                ReplayType.Button,
                0,
                98,
                224,
                140,
            ),
        )
    }

    @Test
    fun iconButton() {
        composeRule.setContentWithExplicitRoot {
            IconButton(onClick = {}) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Content Description")
            }
        }

        assertThat(verifyReplayScreen(viewCount = 11)).contains(
            ReplayRect(ReplayType.Button, 42, 126, 84, 84),
        )
    }

    @Test
    fun textButton() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.wrapContentWidth()) {
                TextButton(onClick = {}) {
                    Text("Button Text")
                }
                TextButton(onClick = {}) {
                    Text("short")
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 13)
        assertThat(capture).contains(ReplayRect(ReplayType.Button, 0, 105, 350, 126))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 28, 140, 294, 57))
        assertThat(capture).contains(ReplayRect(ReplayType.Button, 0, 273, 224, 126))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 45, 308, 135, 57))
    }

    @Test
    fun textButtonIgnoreOneButtonOnly() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.wrapContentWidth()) {
                TextButton(onClick = {}) {
                    Text("Button Text")
                }
                TextButton(onClick = {}, modifier = Modifier.captureIgnore(ignoreSubTree = false)) {
                    Text("short")
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 13)
        assertThat(capture).contains(ReplayRect(ReplayType.Button, 0, 105, 350, 126))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 28, 140, 294, 57))
        assertThat(capture).doesNotContain(ReplayRect(ReplayType.Button, 0, 273, 224, 126))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 45, 308, 135, 57))
    }

    @Test
    fun textButtonIgnoreOneFullTextButton() {
        composeRule.setContentWithExplicitRoot {
            Column(Modifier.wrapContentWidth()) {
                TextButton(onClick = {}) {
                    Text("Button Text")
                }
                TextButton(onClick = {}, modifier = Modifier.captureIgnore(ignoreSubTree = true)) {
                    Text("short")
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 13)
        assertThat(capture).contains(ReplayRect(ReplayType.Button, 0, 105, 350, 126))
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 28, 140, 294, 57))
        assertThat(capture).doesNotContain(ReplayRect(ReplayType.Button, 0, 277, 224, 126))
        assertThat(capture).doesNotContain(ReplayRect(ReplayType.Label, 45, 312, 135, 57))
    }

    @Test
    fun nestedAndroidViewsInsideLayouts() {
        composeRule.setContentWithExplicitRoot {
            Box(Modifier.testTag("root")) {
                Column {
                    AndroidView(::TextView) {
                        it.layoutParams = ViewGroup.LayoutParams(200, 80)
                        it.text = "hi"
                    }
                    AndroidView(::Button) {
                        it.layoutParams = ViewGroup.LayoutParams(200, 80)
                        it.text = "short"
                    }
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 10)
        // Column
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 160))
        // AndroidView w/TextView Top (width reflects the text length
        assertThat(capture).contains(ReplayRect(ReplayType.Label, 3, 84, 33, 37))
        // AndroidView w/Button Bottom
        assertThat(capture).contains(ReplayRect(ReplayType.Button, 0, 164, 200, 80))
    }

    @Test
    fun scanningHandlesDialog() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(300.toDp(), 400.toDp())
                }
            Box(
                Modifier
                    .testTag("parent")
                    .size(width, height),
            ) {
                Dialog(onDismissRequest = {}) {
                    Box(
                        Modifier
                            .testTag("child")
                            .size(width / 2, height / 2),
                    )
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 9)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 300, 400))
        // Dialog - Child Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 0, 150, 200))
    }

    @Test
    fun scanningHandlesWrappedDialog() {
        @Composable
        fun CustomTestDialog(children: @Composable () -> Unit) {
            Dialog(onDismissRequest = {}, content = children)
        }

        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(300.toDp(), 400.toDp())
                }
            Box(
                Modifier
                    .testTag("parent")
                    .size(width, height),
            ) {
                CustomTestDialog {
                    Box(
                        Modifier
                            .testTag("child")
                            .size(width / 2, height / 2),
                    )
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 9)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 300, 400))
        // Dialog - Child Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 0, 150, 200))
    }

    @Test
    fun scanningHandlesSingleSubcomposeLayout_withSingleChild() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(200.toDp(), 400.toDp())
                }
            Box(Modifier.testTag("parent")) {
                SingleSubcompositionLayout(Modifier.testTag("subcompose-layout")) {
                    Box(
                        Modifier
                            .testTag("child")
                            .size(width, height),
                    )
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 7)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
        // Child Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
    }

    @Test
    fun scanningHandlesSingleSubcomposeLayout_withMultipleChildren() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(200.toDp(), 400.toDp())
                }
            Box(Modifier.testTag("parent")) {
                SingleSubcompositionLayout(Modifier.testTag("subcompose-layout")) {
                    Box(
                        Modifier
                            .testTag("child1")
                            .size(width, height),
                    )
                    Box(
                        Modifier
                            .testTag("child2")
                            .size(width, height),
                    )
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 8)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 800))
        // Child Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
        // Child Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 484, 200, 400))
    }

    @Test
    fun scanningHandlesSingleSubcomposeLayout_withMultipleSubcompositionsAndChildren() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(200.toDp(), 400.toDp())
                }
            Box(Modifier.testTag("parent")) {
                MultipleSubcompositionLayout(
                    Modifier.testTag("subcompose-layout"),
                    firstChildren = {
                        Box(
                            Modifier
                                .testTag("child1.1")
                                .size(width, height),
                        )
                        Box(
                            Modifier
                                .testTag("child1.2")
                                .size(width, height),
                        )
                    },
                    secondChildren = {
                        Box(
                            Modifier
                                .testTag("child2.1")
                                .size(width, height),
                        )
                        Box(
                            Modifier
                                .testTag("child2.2")
                                .size(width, height),
                        )
                    },
                )
            }
        }

        val capture = verifyReplayScreen(viewCount = 10)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 1600))
        // First Children - Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
        // First Children - Box 2
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 484, 200, 400))
        // Second Children - Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 884, 200, 400))
        // Second Children - Box 2
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 1284, 200, 400))
    }

    @Test
    fun scanningHandlesSiblingSubcomposeLayouts() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(200.toDp(), 400.toDp())
                }
            Column(Modifier.testTag("parent")) {
                SingleSubcompositionLayout(Modifier.testTag("subcompose-layout1")) {
                    Box(
                        Modifier
                            .testTag("child1")
                            .size(width, height),
                    )
                }
                SingleSubcompositionLayout(Modifier.testTag("subcompose-layout2")) {
                    Box(
                        Modifier
                            .testTag("child2")
                            .size(width, height),
                    )
                }
            }
        }

        val capture = verifyReplayScreen(viewCount = 8)
        // Child Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
        // Child Box 2
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 484, 200, 400))
    }

    @Test
    fun scanningHandlesLazyLists() {
        composeRule.setContent {
            Box(Modifier.testTag("parent")) {
                LazyColumn(Modifier.testTag("list")) {
                    items(listOf(1, 2, 3)) {
                        Box(Modifier.testTag("child:$it"))
                        if (it % 2 == 0) {
                            Box(Modifier.testTag("child:$it (even)"))
                        }
                    }
                }
            }
        }

        // TODO(snowp): Use non-empty views
        verifyReplayScreen(viewCount = 4)
    }

    @Test
    fun scanningSubcomposition_includesSize() {
        composeRule.setContent {
            val (width, height) =
                with(LocalDensity.current) {
                    Pair(200.toDp(), 400.toDp())
                }

            Box(Modifier.testTag("parent")) {
                MultipleSubcompositionLayout(
                    Modifier.testTag("subcompose-layout"),
                    firstChildren = {
                        Box(
                            Modifier
                                .testTag("child1")
                                .size(width, height),
                        )
                        Box(
                            Modifier
                                .testTag("child2")
                                .size(width, height),
                        )
                    },
                    secondChildren = {
                        Box(
                            Modifier
                                .testTag("child3")
                                .size(width, height),
                        )
                    },
                )
            }
        }

        val capture = verifyReplayScreen(viewCount = 9)
        // Parent Box
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 1200))
        // First Children - Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 84, 200, 400))
        // First Children - Box 2
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 484, 200, 400))
        // Second Children - Box 1
        assertThat(capture).contains(ReplayRect(ReplayType.View, 0, 884, 200, 400))
    }

    /**
     * Wrap the call to SubcomposeLayout, since real code almost never calls SubcomposeLayout
     * directly in line, so this more accurately represents a real use case.
     */
    @Composable
    private fun SingleSubcompositionLayout(
        modifier: Modifier,
        children: @Composable () -> Unit,
    ) {
        SubcomposeLayout(modifier) { constraints ->
            val placeables =
                subcompose(Unit, children)
                    .map { it.measure(constraints) }

            layout(
                width = placeables.maxOfOrNull { it.width } ?: 0,
                height = placeables.sumOf { it.height },
            ) {
                placeables.fold(0) { y, placeable ->
                    placeable.placeRelative(0, y)
                    y + placeable.height
                }
            }
        }
    }

    /**
     * Like [SingleSubcompositionLayout] but creates two subcompositions, and lays out all children
     * from both compositions in a column.
     */
    @Composable
    private fun MultipleSubcompositionLayout(
        modifier: Modifier,
        firstChildren: @Composable () -> Unit,
        secondChildren: @Composable () -> Unit,
    ) {
        SubcomposeLayout(modifier) { constraints ->
            val placeables =
                listOf(
                    subcompose(0, firstChildren),
                    subcompose(1, secondChildren),
                ).flatten().map { it.measure(constraints) }

            layout(
                width = placeables.maxOfOrNull { it.width } ?: 0,
                height = placeables.sumOf { it.height },
            ) {
                placeables.fold(0) { y, placeable ->
                    placeable.placeRelative(0, y)
                    y + placeable.height
                }
            }
        }
    }
}
