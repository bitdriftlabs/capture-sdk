// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], shadows = [ShadowRecordingHandlerThread::class])
class JankStatsWarmerTest {
    private val logger: IInternalLogger = mock()
    private val frameMetricsHandlerField: Field =
        Class
            .forName("androidx.metrics.performance.DelegatingFrameMetricsListener")
            .getDeclaredField("frameMetricsHandler")
            .apply { isAccessible = true }

    @Before
    fun setUp() {
        frameMetricsHandlerField.set(null, null)
        ShadowRecordingHandlerThread.mainThreadGetLooperCalls.clear()
    }

    @Test
    fun warmUp_shouldSetFrameMetricsHandlerFromItsOwnThread() {
        val completedOn = warmUpAndAwait(JankStatsWarmer(logger, FakeBackgroundThreadHandler()))

        val handler = frameMetricsHandlerField.get(null) as Handler
        assertThat(handler.looper.thread.name).isEqualTo("FrameMetricsAggregator")
        assertThat(handler.looper.thread.isAlive).isTrue
        assertThat(completedOn).isSameAs(handler.looper.thread)
        assertThat(ShadowRecordingHandlerThread.mainThreadGetLooperCalls).isEmpty()
        verifyNoInteractions(logger)
    }

    @Test
    fun warmUp_withHandlerAlreadySet_shouldKeepItAndComplete() {
        val existingHandler = Handler(Looper.getMainLooper())
        frameMetricsHandlerField.set(null, existingHandler)

        warmUpAndAwait(JankStatsWarmer(logger, FakeBackgroundThreadHandler()))

        assertThat(frameMetricsHandlerField.get(null)).isSameAs(existingHandler)
    }

    @Test
    fun warmUp_belowApi24_shouldCompleteWithoutFrameMetricsHandler() {
        warmUpAndAwait(JankStatsWarmer(logger, FakeBackgroundThreadHandler(), sdkInt = Build.VERSION_CODES.M))

        assertThat(frameMetricsHandlerField.get(null)).isNull()
    }

    private fun warmUpAndAwait(warmer: JankStatsWarmer): Thread {
        val completed = CountDownLatch(1)
        var completedOn: Thread? = null
        warmer.warmUp {
            completedOn = Thread.currentThread()
            completed.countDown()
        }
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue
        return checkNotNull(completedOn)
    }
}
