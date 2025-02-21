// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.Activity
import android.view.Window
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.JankStatsMonitor
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.providers.toFieldValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class JankStatsMonitorTest {
    private val logger: LoggerImpl = mock()
    private val runtime: Runtime = mock()
    private val lifecycle: Lifecycle = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val windowManager: IWindowManager = mock()
    private val mainThreadHandler = Mocks.sameThreadHandler

    private val errorHandler: ErrorHandler = mock()
    private val errorMessageCaptor = argumentCaptor<String>()
    private val illegalStateExceptionCaptor = argumentCaptor<IllegalStateException>()

    private lateinit var window: Window
    private lateinit var jankStatsMonitor: JankStatsMonitor

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        window = activity.window

        whenever(processLifecycleOwner.lifecycle).thenReturn(lifecycle)
        whenever(windowManager.getCurrentWindow()).thenReturn(window)

        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(true)
        whenever(runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)).thenReturn(16)
        whenever(runtime.getConfigValue(RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS)).thenReturn(700)
        whenever(runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)).thenReturn(16)
        whenever(runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)).thenReturn(5000)

        jankStatsMonitor =
            JankStatsMonitor(
                logger,
                processLifecycleOwner,
                runtime,
                windowManager,
                errorHandler,
                mainThreadHandler,
                FakeBackgroundThreadHandler(),
            )
    }

    @Test
    fun onStateChanged_withOnResumeAndSlowFrame_shouldLogWithWarningAndDroppedFrameMessage() {
        val jankDurationInMilli = 690L

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.WARNING, "DroppedFrame")
    }

    @Test
    fun onStateChanged_withOnResumeAndSlowFrame_shouldLogWithErrorAndDroppedFrameMessage() {
        val jankDurationInMilli = 700L

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "DroppedFrame")
    }

    @Test
    fun onStateChanged_withOnResumeAndAnrFrame_shouldLogWithErrorAndAnrMessage() {
        val jankDurationInMilli = 5000L

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "ANR")
    }

    @Test
    fun onStateChanged_withOnResumeAndUpdatedConfigAnrValueAndAnrFrame_shouldLogWithErrorAndAnrMessage() {
        whenever(runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)).thenReturn(2000)
        val jankDurationInMilli = 2000L

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "ANR")
    }

    @Test
    fun onStateChanged_withJankyFrameBelowMinThreshold_shouldNotLogAnyMessage() {
        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = 4L,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onStateChanged_withOnCreateAndAnrFrame_shouldNotLogAnyMessage() {
        val jankDurationInMilli = 5000L

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_CREATE,
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onStateChanged_withOnResumeAndWithoutJankyFrame_shouldNotLogJankFrameData() {
        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = false,
            durationInMilli = 1L,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onStateChanged_withOnResumeJankyFrameAndKillSwitchEnabled_shouldNotLogJankFrameData() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)

        triggerLifecycleEvent(
            lifecycleEvent = Lifecycle.Event.ON_RESUME,
            isJankyFrame = true,
            durationInMilli = 5000,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onStateChanged_withOnResumeAndNullDecorView_shouldReportError() {
        val nullDecorView: Window = mock()
        whenever(windowManager.getCurrentWindow()).thenReturn(nullDecorView)

        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_RESUME)

        verify(errorHandler).handleError(
            errorMessageCaptor.capture(),
            illegalStateExceptionCaptor.capture(),
        )
        assertThat(errorMessageCaptor.lastValue).isEqualTo("Couldn't create JankStats instance")
        assertThat(illegalStateExceptionCaptor.lastValue).isInstanceOf(IllegalStateException::class.java)
        assertThat(
            illegalStateExceptionCaptor.lastValue.message,
        ).isEqualTo("window.peekDecorView() is null: JankStats can only be created with a Window that has a non-null DecorView")
    }

    private fun triggerLifecycleEvent(
        lifecycleEvent: Lifecycle.Event,
        isJankyFrame: Boolean,
        durationInMilli: Long,
    ) {
        jankStatsMonitor.onStateChanged(processLifecycleOwner, lifecycleEvent)

        val frameData =
            FrameData(
                frameStartNanos = 0L,
                frameDurationUiNanos = durationInMilli * 1000000,
                isJank = isJankyFrame,
                states = emptyList(),
            )
        jankStatsMonitor.onFrame(frameData)
    }

    private fun assertLogDetails(
        jankDurationInMilli: Long,
        expectedLogLevel: LogLevel,
        expectedMessage: String,
    ) {
        verify(logger).log(
            eq(LogType.UX),
            eq(expectedLogLevel),
            eq(mapOf("_duration_ms" to jankDurationInMilli.toString().toFieldValue())),
            eq(null),
            eq(null),
            eq(false),
            argThat { message: () -> String -> message.invoke() == expectedMessage },
        )
    }
}
