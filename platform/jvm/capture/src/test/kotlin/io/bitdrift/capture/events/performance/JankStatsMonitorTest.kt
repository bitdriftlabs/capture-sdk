// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

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
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.JankStatsMonitor.JankFrameType
import io.bitdrift.capture.utils.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
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
        whenever(windowManager.findFirstValidActivity()).thenReturn(activity)

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

        assertLogDetails(jankDurationInMilli, JankFrameType.SLOW)
    }

    @Test
    fun onApplicationCreate_withFlagDisabled_shouldNotInteractWithWindowManager() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)

        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_CREATE)
        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 5000L,
        )

        verifyNoInteractions(windowManager)
        verifyNoInteractions(logger)
    }

    @Test
    fun onActivityResumed_withFlagDisabled_shouldNotSetJankStats() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)

        jankStatsMonitor.onActivityResumed(activity)
        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 5000L,
        )

        verify(runtime, never()).getConfigValue(RuntimeConfig.JANK_FRAME_HEURISTICS_MULTIPLIER)
        verifyNoInteractions(logger)
    }

    @Test
    fun onActivityResumed_withSlowFrame_shouldLogWithWarningAndDroppedFrameMessage() {
        val jankDurationInMilli = 690L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, JankFrameType.SLOW)
    }

    @Test
    fun onActivityResumed_withFrozenFrame_shouldLogWithErrorAndDroppedFrameMessage() {
        val jankDurationInMilli = 700L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, JankFrameType.FROZEN)
    }

    @Test
    fun onActivityResumed_withAnrFrame_shouldLogWithErrorAndAnrMessage() {
        val jankDurationInMilli = 5000L
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = jankDurationInMilli,
        )

        assertLogDetails(jankDurationInMilli, JankFrameType.ANR)
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

        assertLogDetails(jankDurationInMilli, JankFrameType.ANR)
    }

    @Test
    fun onActivityResumed_withNegativeFrameDurations_shouldOnlyReportError() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = -1L,
        )

        assertWrongDuration(
            expectedMessage = "Unexpected frame duration. durationInNano: -1000000. durationMillis: -1",
        )
    }

    @Test
    fun onActivityResumed_withOverflownFrameDurations_shouldOnlyReportError() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 9223369584987L,
        )

        assertWrongDuration(
            expectedMessage = "Unexpected frame duration. durationInNano: 9223369584987000000. durationMillis: 9223369584987",
        )
    }

    @Test
    fun onActivityResumed_withJankyFrameBelowMinThreshold_shouldNotLogAnyMessage() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 4L,
        )

        verifyNoInteractions(logger)
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

        verifyNoInteractions(logger)
    }

    @Test
    fun onActivityResumed_withoutJankyFrame_shouldNotLogJankFrameData() {
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = false,
            durationInMilli = 1L,
        )

        verifyNoInteractions(logger)
    }

    @Test
    fun onActivityResumed_withJankyFrameAndKillSwitchEnabled_shouldNotLogJankFrameData() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)
        jankStatsMonitor.onActivityResumed(activity)

        triggerOnFrame(
            isJankyFrame = true,
            durationInMilli = 5000,
        )

        verifyNoInteractions(logger)
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
            argThat<InternalFields> { fields ->
                fields["_duration_ms"]?.toString() == jankDurationInMilli.toString() &&
                    fields["_frame_issue_type"]?.toString() == JankFrameType.SLOW.value.toString() &&
                    fields["_screen_name"]?.toString() == screenName
            },
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
        expectedFrameType: JankStatsMonitor.JankFrameType,
    ) {
        verify(logger).log(
            eq(LogType.UX),
            eq(expectedFrameType.logLevel),
            argThat<InternalFields> { fields ->
                fields["_duration_ms"]?.toString() == jankDurationInMilli.toString() &&
                    fields["_frame_issue_type"]?.toString() == expectedFrameType.value.toString()
            },
            eq(EMPTY_INTERNAL_FIELDS),
            eq(null),
            eq(false),
            argThat { message: () -> String -> message.invoke() == "DroppedFrame" },
        )
    }

    private fun assertWrongDuration(expectedMessage: String) {
        verifyNoInteractions(logger)
        verify(errorHandler).handleError(
            errorMessageCaptor.capture(),
            eq(null),
        )
        assertThat(errorMessageCaptor.lastValue).isEqualTo(expectedMessage)
    }
}
