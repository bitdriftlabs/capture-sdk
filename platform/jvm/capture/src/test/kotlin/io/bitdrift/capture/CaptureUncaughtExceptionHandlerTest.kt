// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.JvmCrashListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class CaptureUncaughtExceptionHandlerTest {
    private val jvmCrashListener: JvmCrashListener = mock()
    private val handler = CaptureUncaughtExceptionHandler
    private lateinit var otherHandler: OtherCrashHandler

    @Before
    fun setUp() {
        otherHandler = OtherCrashHandler()
        Thread.setDefaultUncaughtExceptionHandler(otherHandler)
        handler.install(jvmCrashListener)
    }

    @After
    fun cleanUp() {
        handler.uninstall()
        handler.crashing = false
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun testErrorsAreLoggedByBitdriftAndForwarded() {
        // ARRANGE
        val currentThread = Thread.currentThread()
        val appException = RuntimeException("app crash")

        // ACT
        handler.uncaughtException(currentThread, appException)

        // ASSERT
        verify(jvmCrashListener).onJvmCrash(eq(currentThread), eq(appException))
        assertThat(otherHandler.callArgs.size).isEqualTo(1)
        assertThat(otherHandler.callArgs[0]["thread"]).isEqualTo(currentThread)
        assertThat(otherHandler.callArgs[0]["throwable"]).isEqualTo(appException)
    }

    @Test
    fun testErrorsAreForwardedToOtherHandlerOnBitdriftFailure() {
        // ARRANGE
        whenever(jvmCrashListener.onJvmCrash(any(), any())).thenThrow(RuntimeException("capture logger crash"))

        val currentThread = Thread.currentThread()
        val appException = RuntimeException("app crash")

        // ACT
        handler.uncaughtException(currentThread, appException)

        // ASSERT
        assertThat(otherHandler.callArgs.size).isEqualTo(1)
        assertThat(otherHandler.callArgs[0]["thread"]).isEqualTo(currentThread)
        assertThat(otherHandler.callArgs[0]["throwable"]).isEqualTo(appException)
    }
}

class OtherCrashHandler : Thread.UncaughtExceptionHandler {
    val callArgs = mutableListOf<Map<String, Any>>()

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        callArgs += mapOf("thread" to thread, "throwable" to throwable)
    }
}
