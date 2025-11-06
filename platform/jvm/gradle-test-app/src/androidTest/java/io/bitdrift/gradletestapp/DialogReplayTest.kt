// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
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
import io.bitdrift.gradletestapp.ui.fragments.FirstFragment
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DialogReplayTest {
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
    fun testDialogCapture() {
        // ARRANGE
        scenario.onFragment { fragment ->
            // Show a simple AlertDialog
            val dialog = AlertDialog.Builder(fragment.requireContext())
                .setTitle("Test Dialog")
                .setMessage("This is a test dialog message")
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()

            // Wait a bit for dialog to be fully displayed
            Thread.sleep(500)
        }

        // ACT
        UiThreadStatement.runOnUiThread {
            replayClient.captureScreen()
        }

        // ASSERT
        assert(latch.await(2000, TimeUnit.MILLISECONDS))

        val (screen, metrics) = replay.get()!!

        assertThat(metrics.errorViewCount).isEqualTo(0)
        assertThat(metrics.exceptionCausingViewCount).isEqualTo(0)

        // Check if dialog content is captured
        // Should have buttons from the dialog
        val buttons = screen.filter { it.type == ReplayType.Button }
        assertThat(buttons).isNotEmpty()

        // Should have labels for the dialog title and message
        val labels = screen.filter { it.type == ReplayType.Label }
        assertThat(labels.size).isAtLeast(2) // At least title and message
    }

    @Test
    fun testDialogFragmentCapture() {
        // ARRANGE
        scenario.onFragment { fragment ->
            val dialogFragment = TestDialogFragment()
            dialogFragment.show(fragment.childFragmentManager, "test_dialog")

            // Wait a bit for dialog to be fully displayed
            Thread.sleep(500)
        }

        // ACT
        UiThreadStatement.runOnUiThread {
            replayClient.captureScreen()
        }

        // ASSERT
        assert(latch.await(2000, TimeUnit.MILLISECONDS))

        val (screen, metrics) = replay.get()!!

        assertThat(metrics.errorViewCount).isEqualTo(0)
        assertThat(metrics.exceptionCausingViewCount).isEqualTo(0)

        // Check if dialog fragment content is captured
        val labels = screen.filter { it.type == ReplayType.Label }
        // Should capture both the main content and the dialog content
        assertThat(labels).isNotEmpty()
    }

    class TestDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireContext())
                .setTitle("Dialog Fragment")
                .setMessage("This is a dialog fragment message")
                .setPositiveButton("OK", null)
                .create()
        }
    }
}
