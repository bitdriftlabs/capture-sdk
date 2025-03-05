// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.Activity
import android.app.Application
import android.view.Window
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.StateInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
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
import org.robolectric.RuntimeEnvironment
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

    private lateinit var application: Application
    private lateinit var activity: Activity
    private lateinit var window: Window
    private lateinit var jankStatsMonitor: JankStatsMonitor

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        window = activity.window

        whenever(processLifecycleOwner.lifecycle).thenReturn(lifecycle)
        whenever(windowManager.getCurrentWindow()).thenReturn(window)
        whenever(windowManager.getFirstRootView()).thenReturn(window.decorView)

        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(true)
        whenever(runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)).thenReturn(16)
        whenever(runtime.getConfigValue(RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS)).thenReturn(700)
        whenever(runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)).thenReturn(5000)

        jankStatsMonitor =
            JankStatsMonitor(
                application,
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
    fun onApplicationCreate_withSlowFrame_shouldLogWithWarningAndDroppedFrameMessage() {
        val jankDurationInMilli = 200L
        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_CREATE)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.WARNING, "DroppedFrame")
    }

    @Test
    fun onApplicationActivityResumed_withSlowFrame_shouldNotLogAnyMessage() {
        val jankDurationInMilli = 200L
        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_RESUME)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onActivityResumed_withSlowFrame_shouldLogWithWarningAndDroppedFrameMessage() {
        val jankDurationInMilli = 690L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.WARNING, "DroppedFrame")
    }

    @Test
    fun onActivityResumed_withFrozenFrame_shouldLogWithErrorAndDroppedFrameMessage() {
        val jankDurationInMilli = 700L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "DroppedFrame")
    }

    @Test
    fun onActivityResumed_withAnrFrame_shouldLogWithErrorAndAnrMessage() {
        val jankDurationInMilli = 5000L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "ANR")
    }

    @Test
    fun onActivityResumed_withUpdatedConfigAndAnrFrame_shouldLogWithErrorAndAnrMessage() {
        whenever(runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)).thenReturn(2000)
        val jankDurationInMilli = 2000L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, LogLevel.ERROR, "ANR")
    }

    @Test
    fun onActivityResumed_withJankyFrameBelowMinThreshold_shouldNotLogAnyMessage() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 4L,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onActivityResumed_withAnrFrameAndBelowRuntimeConfig_shouldNotLogAnyMessage() {
        val jankDurationInMilli = 5000L
        whenever(runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)).thenReturn(6000)

        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onActivityResumed_withoutJankyFrame_shouldNotLogJankFrameData() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = false,
            durationInMilli = 1L,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onActivityResumed_withJankyFrameAndKillSwitchEnabled_shouldNotLogJankFrameData() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 5000,
        )

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onActivityResumed_withNullDecorView_shouldReportError() {
        val nullDecorView: Window = mock()
        val mockedActivity: Activity = mock()
        whenever(mockedActivity.window).thenReturn(nullDecorView)

        jankStatsMonitor.onActivityResumed(mockedActivity)

        verify(errorHandler).handleError(
            errorMessageCaptor.capture(),
            illegalStateExceptionCaptor.capture(),
        )
        assertThat(errorMessageCaptor.lastValue).isEqualTo("Couldn't create JankStats instance")
        assertThat(errorMessageCaptor.lastValue).isEqualTo("Couldn't create JankStats instance")
        assertThat(illegalStateExceptionCaptor.lastValue).isInstanceOf(IllegalStateException::class.java)
        assertThat(
            illegalStateExceptionCaptor.lastValue.message,
        ).isEqualTo("window.peekDecorView() is null: JankStats can only be created with a Window that has a non-null DecorView")
    }

    @Test
    fun onActivityPaused_withPreviousOnActivityResumed_shouldStopCollection() {
        jankStatsMonitor.onActivityResumed(activity)

        jankStatsMonitor.onActivityPaused(activity)

        assertThat(jankStatsMonitor.jankStats).isNull()
    }

    @Test
    fun onActivityResumed_withJankyFrameAndScreenNameState_shouldLogScreeNameField() {
        val jankDurationInMilli = 16L
        val screenName = "test"
        jankStatsMonitor.onActivityResumed(activity)
        jankStatsMonitor.trackScreenNameChanged(screenName)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
            states = listOf(StateInfo("_screen_name", screenName)),
        )

        verify(logger).log(
            any(),
            any(),
            eq(
                mapOf(
                    "_duration_ms" to jankDurationInMilli.toString().toFieldValue(),
                    "_screen_name" to screenName.toFieldValue(),
                ),
            ),
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
        )
    }

    private fun triggerOnFrame(
        isJankyFrame: Boolean,
        durationInMilli: Long,
        states: List<StateInfo> = emptyList(),
    ) {
        val frameData =
            FrameData(
                frameStartNanos = 0L,
                frameDurationUiNanos = durationInMilli * 1000000,
                isJank = isJankyFrame,
                states = states,
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
