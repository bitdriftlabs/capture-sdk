// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.AppLifecycleListenerLogger
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppLifecycleListenerLoggerTest {
    private val logger: LoggerImpl = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Mocks.sameThreadHandler

    @Test
    fun testLogsAreFlushedOnStop() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.APP_LIFECYCLE_EVENTS)).thenReturn(true)
        val listener = AppLifecycleListenerLogger(logger, processLifecycleOwner, activityManager, runtime, executor, handler)

        // ACT
        listener.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_STOP)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        // ASSERT
        verify(logger).flush(eq(false))
    }
}
