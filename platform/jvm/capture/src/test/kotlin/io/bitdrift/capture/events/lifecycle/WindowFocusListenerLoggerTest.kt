// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Application
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import org.junit.Before
import org.junit.Test

class WindowFocusListenerLoggerTest {
    private val application: Application = mock()
    private val logger: IInternalLogger = mock()
    private val runtime: Runtime = mock()
    private val handler = Mocks.sameThreadHandler

    private lateinit var windowFocusLogger: WindowFocusListenerLogger

    @Before
    fun setUp() {
        whenever(runtime.isEnabled(RuntimeFeature.WINDOW_FOCUS_FLUSHING)).thenReturn(true)
        windowFocusLogger = WindowFocusListenerLogger(application, logger, runtime, handler)
        windowFocusLogger.start()
    }

    @Test
    fun testFlushOnFocusLost() {
        // ACT
        windowFocusLogger.onWindowFocusChanged(false)

        // ASSERT
        verify(logger).flush(false)
    }

    @Test
    fun testNoFlushOnFocusGained() {
        // ACT
        windowFocusLogger.onWindowFocusChanged(true)

        // ASSERT
        com.nhaarman.mockitokotlin2.verifyZeroInteractions(logger)
    }

    @Test
    fun testNoFlushIfDisabled() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.WINDOW_FOCUS_FLUSHING)).thenReturn(false)

        // ACT
        windowFocusLogger.onWindowFocusChanged(false)

        // ASSERT
        com.nhaarman.mockitokotlin2.verifyZeroInteractions(logger)
    }

    @Test
    fun testNoFlushIfStopped() {
        // ARRANGE
        windowFocusLogger.stop()

        // ACT
        windowFocusLogger.onWindowFocusChanged(false)

        // ASSERT
        com.nhaarman.mockitokotlin2.verifyZeroInteractions(logger)
    }
}
