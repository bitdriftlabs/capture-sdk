// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.events.lifecycle.AppExitLogger
import io.bitdrift.capture.events.lifecycle.CaptureUncaughtExceptionHandler
import org.junit.After
import org.junit.Before
import org.junit.Test

class CaptureUncaughtExceptionHandlerTest {
    private val crashReporter: AppExitLogger = mock()
    private val prevExceptionHandler: Thread.UncaughtExceptionHandler = mock()
    private lateinit var handler: CaptureUncaughtExceptionHandler

    @Before
    fun setUp() {
        handler = CaptureUncaughtExceptionHandler(prevExceptionHandler)
        handler.install(crashReporter)
    }

    @After
    fun cleanUp() {
        handler.uninstall()
    }

    @Test
    fun testErrorsAreLoggedByBitdriftAndForwarded() {
        // ARRANGE
        val currentThread = Thread.currentThread()
        val appException = RuntimeException("app crash")

        // ACT
        handler.uncaughtException(currentThread, appException)

        // ASSERT
        verify(crashReporter).logCrash(currentThread, appException)
        verify(prevExceptionHandler).uncaughtException(currentThread, appException)
    }

    @Test
    fun testErrorsAreForwardedToOtherHandlerOnBitdriftFailure() {
        // ARRANGE
        whenever(crashReporter.logCrash(any(), any())).thenThrow(RuntimeException("capture logger crash"))

        val currentThread = Thread.currentThread()
        val appException = RuntimeException("app crash")

        // ACT
        handler.uncaughtException(currentThread, appException)

        // ASSERT
        verify(prevExceptionHandler).uncaughtException(currentThread, appException)
    }
}
