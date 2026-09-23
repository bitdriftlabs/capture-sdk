// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
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
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.JankStatsMonitor.JankFrameType
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.providers.ArrayFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class JankStatsMonitorTest {
    private val logger: IInternalLogger = mock()
    private val runtime: Runtime = mock()
    private val lifecycle: Lifecycle = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val windowManager: IWindowManager = mock()
    private val mainThreadHandler = Mocks.sameThreadHandler
    private val warmUpCallbacks = mutableListOf<() -> Unit>()
    private val jankStatsWarmer = IJankStatsWarmer { onComplete -> warmUpCallbacks.add(onComplete) }
    private val immediateIdleScheduler = IMainThreadIdleScheduler { it() }

    private val illegalStateExceptionCaptor = argumentCaptor<IllegalStateException>()
    private val logMessageCaptor = argumentCaptor<() -> String>()

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
                mainThreadHandler,
                jankStatsWarmer = jankStatsWarmer,
                mainThreadIdleScheduler = immediateIdleScheduler,
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

        verify(logger).logInternal(
            type = eq(LogType.INTERNALSDK),
            level = eq(LogLevel.ERROR),
            arrayFields = eq(ArrayFields.EMPTY),
            throwable = illegalStateExceptionCaptor.capture(),
            blocking = eq(false),
            message = logMessageCaptor.capture(),
        )
        assertThat(logMessageCaptor.firstValue()).isEqualTo("Couldn't create JankStats instance")
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

        verify(logger).logInternal(
            any(),
            any(),
            argThat<ArrayFields> { fields ->
                fields["_duration_ms"] == jankDurationInMilli.toString() &&
                    fields["_frame_issue_type"] == JankFrameType.SLOW.value.toString() &&
                    fields["_screen_name"] == screenName
            },
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
        )
    }

    @Test
    fun onActivityDestroyed_afterOnStateChangedAttachesJankStats_shouldClearJankStatsWithoutLeak() {
        // 1. SDK starts, onStateChanged ON_CREATE and ON_RESUME triggers findFirstValidActivity
        //    which returns our activity and attaches JankStats to its window
        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_CREATE)
        jankStatsMonitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_RESUME)

        assertThat(jankStatsMonitor.jankStats).isNotNull()

        // 2. Activity is destroyed WITHOUT going through onActivityPaused
        //    (simulates finish() from onCreate, or activity was already paused when SDK started)
        jankStatsMonitor.onActivityDestroyed(activity)

        // 3. Prior Leak: jankStats wasn't null before
        assertThat(jankStatsMonitor.jankStats).isNull()

        jankStatsMonitor.onActivityResumed(activity)

        assertThat(jankStatsMonitor.jankStats).isNotNull
    }

    @Test
    @Config(shadows = [ShadowRecordingHandlerThread::class])
    fun start_thenActivityResumed_shouldNotWaitOnHandlerThreadLooperFromMainThread() {
        // JankStats creates its "FrameMetricsAggregator" HandlerThread lazily, once per process,
        // and keeps it in a static. Clear it so this test always exercises the first creation.
        resetJankStatsFrameMetricsHandler()
        ShadowRecordingHandlerThread.mainThreadGetLooperCalls.clear()
        val backgroundExecutor = Executors.newSingleThreadExecutor()
        val monitor =
            JankStatsMonitor(
                application,
                logger,
                processLifecycleOwner,
                runtime,
                windowManager,
                mainThreadHandler = MainThreadHandler(),
                backgroundThreadHandler =
                    object : IBackgroundThreadHandler {
                        override fun runAsync(task: () -> Unit) = backgroundExecutor.execute(task)
                    },
                mainThreadIdleScheduler = MainThreadIdleScheduler(),
            )
        val resumedActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        markAttachedWindowHardwareAccelerated(resumedActivity.window)

        monitor.start()
        // The warm-up finishes on its own thread and then registers the observers on the main thread
        awaitMainThreadCondition { mockingDetails(lifecycle).invocations.any { it.method.name == "addObserver" } }
        monitor.onActivityResumed(resumedActivity)
        // Run everything deferred to the main thread, since a main-thread wait inside a posted
        // runnable or an idle handler ANRs just the same.
        idleMainLooperAndRunIdleHandlers()

        assertThat(monitor.jankStats).isNotNull
        assertThat(ShadowRecordingHandlerThread.mainThreadGetLooperCalls).isEmpty()
    }

    @Test
    fun start_withFlagEnabled_shouldRegisterObserversOnlyAfterWarmUp() {
        val monitor = buildMonitor()

        monitor.start()

        verify(lifecycle, never()).addObserver(monitor)
        warmUpCallbacks.single().invoke()
        verify(lifecycle).addObserver(monitor)
    }

    @Test
    fun start_withFlagDisabled_shouldNotWarmUpJankStats() {
        whenever(runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)).thenReturn(false)
        val monitor = buildMonitor()

        monitor.start()

        assertThat(warmUpCallbacks).isEmpty()
        verify(lifecycle).addObserver(monitor)
    }

    @Test
    fun start_withStopBeforeWarmUpFinishes_shouldNotRegisterObservers() {
        val monitor = buildMonitor()

        monitor.start()
        monitor.stop()
        warmUpCallbacks.single().invoke()

        verify(lifecycle, never()).addObserver(monitor)
    }

    @Test
    fun onActivityResumed_shouldAttachJankStatsOnlyOnceMainThreadIsIdle() {
        val monitor = buildMonitor(mainThreadIdleScheduler = MainThreadIdleScheduler())

        monitor.onActivityResumed(activity)

        assertThat(monitor.jankStats).isNull()
        idleMainLooperAndRunIdleHandlers()
        assertThat(monitor.jankStats).isNotNull
    }

    @Test
    fun onActivityPaused_beforeMainThreadIsIdle_shouldNotAttachJankStats() {
        val monitor = buildMonitor(mainThreadIdleScheduler = MainThreadIdleScheduler())

        monitor.onActivityResumed(activity)
        monitor.onActivityPaused(activity)
        idleMainLooperAndRunIdleHandlers()

        assertThat(monitor.jankStats).isNull()
    }

    @Test
    fun onStateChangedAndOnActivityResumed_forSameWindow_shouldAttachJankStatsOnce() {
        val monitor = buildMonitor(mainThreadIdleScheduler = MainThreadIdleScheduler())

        monitor.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_RESUME)
        monitor.onActivityResumed(activity)
        idleMainLooperAndRunIdleHandlers()

        verify(runtime, times(1)).getConfigValue(RuntimeConfig.JANK_FRAME_HEURISTICS_MULTIPLIER)
    }

    // Robolectric only runs idle handlers after it processes a message, while a device runs them
    // whenever the queue goes idle. Post an empty message so pending idle handlers run.
    private fun idleMainLooperAndRunIdleHandlers() {
        Handler(Looper.getMainLooper()).post {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitMainThreadCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        while (!condition()) {
            assertThat(System.currentTimeMillis()).isLessThan(deadline)
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun buildMonitor(
        backgroundThreadHandler: IBackgroundThreadHandler = FakeBackgroundThreadHandler(),
        mainThreadIdleScheduler: IMainThreadIdleScheduler = immediateIdleScheduler,
    ): JankStatsMonitor =
        JankStatsMonitor(
            application,
            logger,
            processLifecycleOwner,
            runtime,
            windowManager,
            mainThreadHandler,
            backgroundThreadHandler,
            jankStatsWarmer,
            mainThreadIdleScheduler,
        )

    // Real devices render with hardware acceleration, and JankStats only creates its HandlerThread
    // for hardware-accelerated windows. Robolectric attaches windows without it.
    private fun markAttachedWindowHardwareAccelerated(window: Window) {
        val attachInfo = ReflectionHelpers.getField<Any>(window.decorView, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
        assertThat(window.decorView.isHardwareAccelerated).isTrue
    }

    private fun resetJankStatsFrameMetricsHandler() {
        Class
            .forName("androidx.metrics.performance.DelegatingFrameMetricsListener")
            .getDeclaredField("frameMetricsHandler")
            .apply { isAccessible = true }
            .set(null, null)
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
        verify(logger).logInternal(
            eq(LogType.UX),
            eq(expectedFrameType.logLevel),
            argThat<ArrayFields> { fields ->
                fields["_duration_ms"] == jankDurationInMilli.toString() &&
                    fields["_frame_issue_type"] == expectedFrameType.value.toString()
            },
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            argThat { message: () -> String -> message.invoke() == "DroppedFrame" },
        )
    }

    private fun assertWrongDuration(expectedMessage: String) {
        verify(logger).logInternal(
            type = eq(LogType.INTERNALSDK),
            level = eq(LogLevel.ERROR),
            arrayFields = eq(ArrayFields.EMPTY),
            throwable = anyOrNull(),
            blocking = eq(false),
            message = logMessageCaptor.capture(),
        )
        assertThat(logMessageCaptor.firstValue()).isEqualTo(expectedMessage)
    }
}
