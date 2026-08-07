// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity
import android.app.Application
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class WindowFocusListenerLoggerTest {
    private val logger: IInternalLogger = mock()
    private val runtime: Runtime = mock()
    private val mainThreadHandler = Mocks.sameThreadHandler

    private lateinit var application: Application
    private lateinit var windowFocusListenerLogger: WindowFocusListenerLogger

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        whenever(runtime.isEnabled(RuntimeFeature.WINDOW_FOCUS_FLUSHING)).thenReturn(true)

        windowFocusListenerLogger =
            WindowFocusListenerLogger(
                application,
                logger,
                runtime,
                mainThreadHandler,
            )
    }

    @Test
    fun onWindowFocusChanged_withFocusLost_shouldFlushLogger() {
        windowFocusListenerLogger.onWindowFocusChanged(false)

        verify(logger).flush(eq(false))
    }

    @Test
    fun onWindowFocusChanged_withFocusGained_shouldNotFlushLogger() {
        windowFocusListenerLogger.onWindowFocusChanged(true)

        verifyNoInteractions(logger)
    }

    @Test
    fun onWindowFocusChanged_withFlagDisabled_shouldNotFlushLogger() {
        whenever(runtime.isEnabled(RuntimeFeature.WINDOW_FOCUS_FLUSHING)).thenReturn(false)

        windowFocusListenerLogger.onWindowFocusChanged(false)

        verifyNoInteractions(logger)
    }

    @Test
    fun onActivityStarted_withStartedListener_shouldTrackActivityRootView() {
        windowFocusListenerLogger.start()

        val controller = buildActivity()

        assertThat(windowFocusListenerLogger.trackedRootViews).containsKey(controller.get())
    }

    @Test
    fun onActivityStarted_withSameActivityStartedTwice_shouldTrackActivityOnce() {
        windowFocusListenerLogger.start()

        val controller = buildActivity()
        controller.stop().start()

        assertThat(windowFocusListenerLogger.trackedRootViews).hasSize(1)
    }

    @Test
    fun onActivityDestroyed_withTrackedActivity_shouldStopTrackingActivity() {
        windowFocusListenerLogger.start()
        val controller = buildActivity()

        controller.pause().stop().destroy()

        assertThat(windowFocusListenerLogger.trackedRootViews).isEmpty()
    }

    @Test
    fun onStop_withTrackedActivity_shouldStopTrackingAndIgnoreFurtherActivities() {
        windowFocusListenerLogger.start()
        buildActivity()

        windowFocusListenerLogger.stop()
        buildActivity()

        assertThat(windowFocusListenerLogger.trackedRootViews).isEmpty()
    }

    @Test
    fun onWindowFocusChanged_withRealActivityFocusLoss_shouldFlushLogger() {
        windowFocusListenerLogger.start()
        val controller = buildActivity()

        controller.windowFocusChanged(false)

        verify(logger).flush(eq(false))
    }

    private fun buildActivity(): ActivityController<Activity> =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
}
