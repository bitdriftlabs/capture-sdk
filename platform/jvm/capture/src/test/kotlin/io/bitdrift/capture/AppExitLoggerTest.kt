// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.AppExitLogger
import io.bitdrift.capture.events.lifecycle.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.events.performance.MemoryMetricsProvider
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.BuildVersionChecker
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import java.io.IOException
import java.nio.charset.StandardCharsets

class AppExitLoggerTest {
    private val logger: LoggerImpl = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()
    private val errorHandler: ErrorHandler = mock()
    private val crashHandler: CaptureUncaughtExceptionHandler = mock()
    private val versionChecker: BuildVersionChecker = mock()
    private val memoryMetricsProvider: MemoryMetricsProvider = mock()
    private val appExitLogger =
        AppExitLogger(
            logger,
            activityManager,
            runtime,
            errorHandler,
            crashHandler,
            versionChecker,
            memoryMetricsProvider,
        )

    @Before
    fun setUp() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(true)
        whenever(versionChecker.isAtLeast(anyInt())).thenReturn(true)
    }

    @Test
    fun testLoggerIsNotInstalledWhenRuntimeDisabled() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(false)
        // ACT
        appExitLogger.installAppExitLogger()
        // ASSERT
        verify(crashHandler, never()).install(any())
        verify(activityManager, never()).setProcessStateSummary(any())
        verify(activityManager, never()).getHistoricalProcessExitReasons(anyOrNull(), any(), any())
    }

    @Test
    fun testLoggerIsInstalledWhenRuntimeEnabled() {
        // ARRANGE
        whenever(logger.sessionId).thenReturn("test-session-id")

        // ACT
        appExitLogger.installAppExitLogger()
        // ASSERT
        verify(crashHandler).install(appExitLogger)
        verify(activityManager).setProcessStateSummary(any())
        verify(activityManager).getHistoricalProcessExitReasons(anyOrNull(), any(), any())
    }

    @Test
    fun testHandlerIsUninstalled() {
        // ARRANGE

        // ACT
        appExitLogger.uninstallAppExitLogger()

        // ASSERT
        verify(crashHandler).uninstall()
    }

    @Test
    fun testSavePassedSessionId() {
        // ARRANGE

        // ACT
        appExitLogger.saveCurrentSessionId("test-session-id")

        // ASSERT
        verify(activityManager).setProcessStateSummary("test-session-id".toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun testSaveCurrentSessionId() {
        // ARRANGE
        whenever(logger.sessionId).thenReturn("test-session-id")

        // ACT
        appExitLogger.saveCurrentSessionId()

        // ASSERT
        verify(activityManager).setProcessStateSummary("test-session-id".toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun testSaveCurrentSessionIdReportsError() {
        val error = RuntimeException("test exception")
        // ARRANGE
        whenever(activityManager.setProcessStateSummary(any())).thenThrow(error)

        // ACT
        appExitLogger.saveCurrentSessionId("test-session-id")

        // ASSERT
        verify(errorHandler).handleError(any(), eq(error))
    }

    @Test
    fun testLogPreviousExitReasonIfAny() {
        // ARRANGE
        val sessionId = "test-session-id"
        val timestamp = 123L
        val mockExitInfo = mock<ApplicationExitInfo>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockExitInfo.processStateSummary).thenReturn(sessionId.toByteArray(StandardCharsets.UTF_8))
        whenever(mockExitInfo.timestamp).thenReturn(timestamp)
        whenever(mockExitInfo.processName).thenReturn("test-process-name")
        whenever(mockExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_ANR)
        whenever(mockExitInfo.importance).thenReturn(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        whenever(mockExitInfo.status).thenReturn(0)
        whenever(mockExitInfo.pss).thenReturn(1)
        whenever(mockExitInfo.rss).thenReturn(2)
        whenever(mockExitInfo.description).thenReturn("test-description")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        val expectedFields =
            mapOf(
                "_app_exit_source" to "ApplicationExitInfo",
                "_app_exit_process_name" to "test-process-name",
                "_app_exit_reason" to "ANR",
                "_app_exit_importance" to "FOREGROUND",
                "_app_exit_status" to "0",
                "_app_exit_pss" to "1",
                "_app_exit_rss" to "2",
                "_app_exit_description" to "test-description",
            ).toFields()
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            eq(expectedFields),
            eq(null),
            eq(LogAttributesOverrides(sessionId, timestamp)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
    }

    @Test
    fun testLogPreviousExitReasonIfAnyReportsError() {
        // ARRANGE
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(errorHandler).handleError(any(), eq(error))
        Mockito.verifyNoInteractions(logger)
    }

    @Test
    fun testHandlerCrashLogs() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_CRASH)).thenReturn(true)
        val currentThread = Thread.currentThread()
        val appException = IOException("real app crash")
        // ACT
        appExitLogger.logCrash(currentThread, RuntimeException("wrapper crash", appException))
        // ASSERT
        val expectedFields =
            mapOf(
                "_app_exit_source" to "UncaughtExceptionHandler",
                "_app_exit_reason" to "Crash",
                "_app_exit_info" to appException.javaClass.name,
                "_app_exit_details" to appException.message.orEmpty(),
                "_app_exit_thread" to currentThread.name,
            ).toFields()
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            eq(expectedFields),
            eq(null),
            eq(null),
            eq(true),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
        verify(logger).flush(true)
    }
}
