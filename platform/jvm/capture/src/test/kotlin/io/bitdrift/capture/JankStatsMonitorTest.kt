// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.Activity
import android.view.Window
import androidx.metrics.performance.FrameData
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.JankFrameType
import io.bitdrift.capture.events.performance.JankStatsMonitor
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.providers.toFields
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
    private val errorHandler: ErrorHandler = mock()
    private val errorMessageCaptor = argumentCaptor<String>()
    private val illegalStateExceptionCaptor = argumentCaptor<IllegalStateException>()

    private lateinit var window: Window
    private lateinit var jankStatsMonitor: JankStatsMonitor

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        window = activity.window

        whenever(runtime.isEnabled(RuntimeFeature.JANK_STATS_EVENTS)).thenReturn(true)
        jankStatsMonitor = JankStatsMonitor(logger, runtime, errorHandler, FakeBackgroundThreadHandler())
    }

    @Test
    fun onFrame_withSlowJankyFrame_shouldLogJankFrameData() {
        val jankDurationInMilli = 50L

        triggerOnFrame(isJankyFrame = true, durationInMilli = jankDurationInMilli)

        assertJankFrameData(durationInMilli = jankDurationInMilli, jankType = JankFrameType.SLOW)
    }

    @Test
    fun onFrame_withFrozenJankyFrame_shouldLogJankFrameData() {
        val jankDurationInMilli = 700L

        triggerOnFrame(isJankyFrame = true, durationInMilli = jankDurationInMilli)

        assertJankFrameData(durationInMilli = jankDurationInMilli, jankType = JankFrameType.FROZEN)
    }

    @Test
    fun onFrame_withANRJankyFrame_shouldLogJankFrameData() {
        val jankDurationInMilli = 5000L

        triggerOnFrame(isJankyFrame = true, durationInMilli = jankDurationInMilli)

        assertJankFrameData(durationInMilli = jankDurationInMilli, jankType = JankFrameType.ANR)
    }

    @Test
    fun onFrame_withoutJankyFrame_shouldNotLogJankFrameData() {
        triggerOnFrame(isJankyFrame = false, durationInMilli = 1L)

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onFrame_withJankyFrameAndKillSwitchEnabled_shouldNotLogJankFrameData() {
        whenever(runtime.isEnabled(RuntimeFeature.JANK_STATS_EVENTS)).thenReturn(false)

        triggerOnFrame(isJankyFrame = true, durationInMilli = 5000)

        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun onWindowAvailable_withNullDecorView_shouldReportError() {
        jankStatsMonitor.onWindowAvailable(mock())

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

    private fun triggerOnFrame(
        isJankyFrame: Boolean,
        durationInMilli: Long,
    ) {
        jankStatsMonitor.onWindowAvailable(window)

        val frameData =
            FrameData(
                frameStartNanos = 0L,
                frameDurationUiNanos = durationInMilli * 1000000,
                isJank = isJankyFrame,
                states = emptyList(),
            )

        jankStatsMonitor.onFrame(frameData)
    }

    private fun assertJankFrameData(
        durationInMilli: Long,
        jankType: JankFrameType,
    ) {
        val expectedFields =
            buildMap {
                put("_jank_frame_duration_ms", durationInMilli.toString())
                put("_jank_frame_type", jankType.toString())
            }.toFields()

        verify(logger).log(
            eq(LogType.UX),
            eq(LogLevel.ERROR),
            eq(expectedFields),
            eq(null),
            eq(null),
            eq(false),
            argThat { message: () -> String -> message.invoke() == "JankFrame" },
        )
    }
}
