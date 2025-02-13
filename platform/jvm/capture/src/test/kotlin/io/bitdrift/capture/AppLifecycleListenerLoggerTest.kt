// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.AppLifecycleListenerLogger
import io.bitdrift.capture.events.lifecycle.ILifecycleWindowListener
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AppLifecycleListenerLoggerTest {
    private val logger: LoggerImpl = mock()
    private val windowManager: IWindowManager = mock()
    private val lifecycleWindowListener: ILifecycleWindowListener = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val runtime: Runtime = mock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Mocks.sameThreadHandler
    private lateinit var appLifecycleListenerLogger: AppLifecycleListenerLogger
    private val lifecycle: Lifecycle = mock()

    @Before
    fun setUp() {
        whenever(processLifecycleOwner.lifecycle).thenReturn(lifecycle)
        appLifecycleListenerLogger =
            AppLifecycleListenerLogger(
                logger,
                processLifecycleOwner,
                runtime,
                executor,
                handler,
                windowManager,
                lifecycleWindowListener,
            )
        whenever(runtime.isEnabled(RuntimeFeature.APP_LIFECYCLE_EVENTS)).thenReturn(true)
        appLifecycleListenerLogger.start()
    }

    @Test
    fun onStateChanged_whenOnStop_shouldFlushAndNotifyWindowRemoved() {
        whenever(windowManager.getCurrentWindow()).thenReturn(mock())

        triggerLifecycleEvent(Lifecycle.Event.ON_STOP)

        verify(logger).flush(eq(false))
        verify(lifecycleWindowListener).onWindowRemoved()
        verify(lifecycleWindowListener, never()).onWindowAvailable(any())
    }

    @Test
    fun onStateChanged_whenOnResume_shouldEmitWindowAvailable() {
        whenever(windowManager.getCurrentWindow()).thenReturn(mock())

        triggerLifecycleEvent(Lifecycle.Event.ON_CREATE)
        triggerLifecycleEvent(Lifecycle.Event.ON_START)
        triggerLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(lifecycleWindowListener).onWindowAvailable(any())
        verify(lifecycleWindowListener, never()).onWindowRemoved()
        verify(logger, never()).flush(any())
    }

    @Test
    fun onStateChanged_whenOnResumeAndInvalidWindow_shouldNotEmitWindowAvailable() {
        whenever(windowManager.getCurrentWindow()).thenReturn(null)

        triggerLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(lifecycleWindowListener, never()).onWindowAvailable(any())
        verify(lifecycleWindowListener, never()).onWindowRemoved()
        verify(logger, never()).flush(any())
    }

    private fun triggerLifecycleEvent(lifecycle: Lifecycle.Event) {
        appLifecycleListenerLogger.onStateChanged(processLifecycleOwner, lifecycle)
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }
}
