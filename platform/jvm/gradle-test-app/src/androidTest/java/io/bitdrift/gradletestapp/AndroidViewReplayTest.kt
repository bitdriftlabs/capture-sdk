// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.replay.internal.ReplayRect
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// These tests run via github actions using a Nexus 6 API 21 which has a screen size of 1440 x 2560
// emulator -avd Nexus_6_API_21 \
// -no-window -accel on -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
class AndroidViewReplayTest {

    private lateinit var scenario: FragmentScenario<FirstFragment>

    private lateinit var replayClient: ReplayPreviewClient
    private val replay: AtomicReference<Pair<FilteredCapture, ReplayCaptureMetrics>?> = AtomicReference(null)
    private lateinit var latch: CountDownLatch

    @Before
    fun setUp() {
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_MyApplication, initialState = Lifecycle.State.RESUMED)

        latch = CountDownLatch(1)
        replayClient = TestUtils.createReplayPreviewClient(replay, latch, InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun testBasicLayout() {
        // ARRANGE

        // ACT
        UiThreadStatement.runOnUiThread {
            replayClient.captureScreen()
        }

        // ASSERT
        scenario.onFragment {
            assert(latch.await(1000, TimeUnit.MILLISECONDS))

            val (screen, metrics) = replay.get()!!

            assertThat(metrics.errorViewCount).isEqualTo(0)
            assertThat(metrics.exceptionCausingViewCount).isEqualTo(0)
            // AppCompatTextView multiline label
            assertThat(screen).contains(ReplayRect(type = ReplayType.Label, x = 36, y = 421, width = 758, height = 47))
            assertThat(screen).contains(ReplayRect(type = ReplayType.Label, x = 36, y = 463, width = 698, height = 38))
        }
    }
}
