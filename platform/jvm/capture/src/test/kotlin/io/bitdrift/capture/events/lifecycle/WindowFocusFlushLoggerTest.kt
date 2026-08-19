// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity
import android.app.Application
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.fakes.FakeRuntime
import io.bitdrift.capture.fakes.FakeWindowFocusRegistrar
import io.bitdrift.capture.fakes.FakeWindowManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Drives [WindowFocusFlushLogger] through [FakeWindowFocusRegistrar], so window-focus changes are
 * plain function calls — no `Window`, no `ViewTreeObserver`. The real focus mechanism is covered
 * separately by `ViewTreeWindowFocusRegistrarTest`.
 */
class WindowFocusFlushLoggerTest {
    private val application: Application = mock()
    private val logger: IInternalLogger = mock()
    private val runtime = FakeRuntime()
    private val windowManager = FakeWindowManager()
    private val focusRegistrar = FakeWindowFocusRegistrar()
    private val mainThreadHandler = Mocks.sameThreadHandler

    private val activity: Activity = mock()
    private val secondActivity: Activity = mock()

    private lateinit var windowFocusFlushLogger: WindowFocusFlushLogger

    @Before
    fun setUp() {
        windowFocusFlushLogger =
            WindowFocusFlushLogger(
                application,
                logger,
                runtime,
                windowManager,
                focusRegistrar,
                mainThreadHandler,
            )
    }

    @Test
    fun flushesNonBlockingWhenAStartedActivityLosesFocus() {
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        focusRegistrar.changeFocus(activity, hasFocus = false)

        verify(logger).flush(false)
    }

    @Test
    fun doesNotFlushWhenFocusIsGained() {
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        focusRegistrar.changeFocus(activity, hasFocus = true)

        verify(logger, never()).flush(false)
    }

    @Test
    fun doesNotFlushWhenTheKillSwitchIsDisabled() {
        runtime.setEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_WINDOW_FOCUS_LOSS, false)
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        focusRegistrar.changeFocus(activity, hasFocus = false)

        verify(logger, never()).flush(false)
    }

    @Test
    fun rereadsTheKillSwitchOnEveryFocusLossWithoutARestart() {
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        focusRegistrar.changeFocus(activity, hasFocus = false)
        runtime.setEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_WINDOW_FOCUS_LOSS, false)
        focusRegistrar.changeFocus(activity, hasFocus = false)

        verify(logger, times(1)).flush(false)
    }

    @Test
    fun registersAnActivityThatWasAlreadyStartedBeforeTheSdk() {
        // The launcher activity can be started before the events listener target starts; it never
        // gets an onActivityStarted, so start() must pick it up from the window manager.
        windowManager.firstValidActivity = activity

        windowFocusFlushLogger.start()

        assertThat(focusRegistrar.registeredActivities).containsExactly(activity)
    }

    @Test
    fun unregistersAnActivityWhenItStops() {
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        windowFocusFlushLogger.onActivityStopped(activity)

        assertThat(focusRegistrar.registeredActivities).isEmpty()
    }

    @Test
    fun stopPreventsAnyFurtherFlushes() {
        // The kill switch stays enabled: stopping the listener itself must be enough. A listener
        // that was registered before stop() keeps receiving platform focus callbacks (nothing
        // removed it), and those must not reach a logger that is being torn down.
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)

        windowFocusFlushLogger.stop()
        focusRegistrar.changeFocus(activity, hasFocus = false)

        verify(logger, never()).flush(false)
    }

    @Test
    fun stopUnregistersEveryRemainingFocusObserver() {
        // After stop() the lifecycle callbacks are gone, so the per-activity unregistration in
        // onActivityStopped/onActivityDestroyed can never run again — stop() itself is the last
        // chance to tear these down.
        windowFocusFlushLogger.start()
        windowFocusFlushLogger.onActivityStarted(activity)
        windowFocusFlushLogger.onActivityStarted(secondActivity)

        windowFocusFlushLogger.stop()

        assertThat(focusRegistrar.registeredActivities).isEmpty()
    }
}
