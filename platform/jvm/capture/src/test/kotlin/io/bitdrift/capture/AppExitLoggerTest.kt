// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
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
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.FAKE_EXCEPTION
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.SESSION_ID
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.TIME_STAMP
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider.Companion.DEFAULT_MEMORY_ATTRIBUTES_MAP
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_EMPTY_LIST_MESSAGE
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_EXCEPTION_MESSAGE
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_UNMATCHED_PROCESS_NAME_MESSAGE
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.utils.BuildVersionChecker
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import java.io.IOException
import java.nio.charset.StandardCharsets

class AppExitLoggerTest {
    private val logger: LoggerImpl = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()

    private val errorHandler: ErrorHandler = mock()
    private val versionChecker: BuildVersionChecker = mock()
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()
    private val backgroundThreadHandler = FakeBackgroundThreadHandler()
    private val lastExitInfo = FakeLatestAppExitInfoProvider()
    private lateinit var appExitLogger: AppExitLogger

    @Before
    fun setUp() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(true)
        whenever(versionChecker.isAtLeast(anyInt())).thenReturn(true)
        appExitLogger = buildAppExitLogger()
        lastExitInfo.reset()
    }

    @Test
    fun testLoggerIsNotInstalledWhenRuntimeDisabled() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(false)
        // ACT
        appExitLogger.installAppExitLogger()
        // ASSERT
        verify(captureUncaughtExceptionHandler, never()).install(any())
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
        verify(captureUncaughtExceptionHandler).install(appExitLogger)
        verify(activityManager).setProcessStateSummary(any())
    }

    @Test
    fun testHandlerIsUninstalled() {
        // ARRANGE

        // ACT
        appExitLogger.uninstallAppExitLogger()

        // ASSERT
        verify(captureUncaughtExceptionHandler).uninstall()
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
    fun logPreviousExitReasonIfAny_withValidReasonAndProcessSummary_shouldEmitAppExitLog() {
        // ARRANGE
        lastExitInfo.setAsValidReason(
            exitReasonType = ApplicationExitInfo.REASON_ANR,
            description = "test-description",
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            eq(buildExpectedAnrFields()),
            eq(null),
            eq(LogAttributesOverrides.SessionID(SESSION_ID, TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
    }

    @Test
    fun logPreviousExitReasonIfAny_withValidReasonAndInvalidProcessSummary_shouldReportErrorOnly() {
        // ARRANGE
        lastExitInfo.setAsValidReason(
            exitReasonType = ApplicationExitInfo.REASON_ANR,
            description = "test-description",
            processStateSummary = null,
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(logger, never()).log(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
        verify(errorHandler).handleError("AppExitLogger: processStateSummary from test-process-name is null.")
    }

    @Test
    fun logPreviousExitReason_whenEmptyResult_shouldReportError() {
        // ARRANGE
        lastExitInfo.setAsEmptyReason()

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(errorHandler).handleError(EXIT_REASON_EMPTY_LIST_MESSAGE)
        verify(logger, never()).log(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun logPreviousExitReason_withoutMatchingProcessName_shouldReportError() {
        // ARRANGE
        lastExitInfo.setAsInvalidProcessName()

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(errorHandler).handleError(EXIT_REASON_UNMATCHED_PROCESS_NAME_MESSAGE)
        verify(logger, never()).log(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun logPreviousExitReason_whenErrorResult_shouldReportEmptyReason() {
        // ARRANGE
        lastExitInfo.setAsErrorResult()

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verify(errorHandler).handleError(EXIT_REASON_EXCEPTION_MESSAGE, FAKE_EXCEPTION)
        verify(logger, never()).log(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun testHandlerCrashLogs() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_CRASH)).thenReturn(true)
        val currentThread = Thread.currentThread()
        val appException = IOException("real app crash")

        // ACT
        appExitLogger.onJvmCrash(currentThread, RuntimeException("wrapper crash", appException))

        // ASSERT
        val expectedFields =
            buildMap {
                put("_app_exit_source", "UncaughtExceptionHandler")
                put("_app_exit_reason", "Crash")
                put("_app_exit_info", appException.javaClass.name)
                put("_app_exit_details", appException.message.orEmpty())
                put("_app_exit_thread", currentThread.name)
                putAll(DEFAULT_MEMORY_ATTRIBUTES_MAP)
            }.toFields()
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

    @Test
    fun onJvmCrash_whenBuiltInFatalIssueMechanism_shouldNotSendAppExitCrashLog() {
        val appExitLogger = buildAppExitLogger(FatalIssueMechanism.BuiltIn)
        whenever(runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_CRASH)).thenReturn(true)

        appExitLogger.onJvmCrash(Thread.currentThread(), IllegalStateException("Simulated Crash"))

        verify(logger, never()).log(
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
        )
        verify(logger, never()).flush(any())
    }

    private fun buildAppExitLogger(fatalIssueMechanism: FatalIssueMechanism = FatalIssueMechanism.None) =
        AppExitLogger(
            logger,
            activityManager,
            runtime,
            errorHandler,
            versionChecker,
            memoryMetricsProvider,
            backgroundThreadHandler,
            lastExitInfo,
            captureUncaughtExceptionHandler,
            fatalIssueMechanism,
        )

    private fun buildExpectedAnrFields(): Map<String, FieldValue> =
        buildMap {
            put("_app_exit_source", "ApplicationExitInfo")
            put("_app_exit_process_name", "test-process-name")
            put("_app_exit_reason", "ANR")
            put("_app_exit_importance", "FOREGROUND")
            put("_app_exit_status", "0")
            put("_app_exit_pss", "1")
            put("_app_exit_rss", "2")
            put("_app_exit_description", "test-description")
            put("_memory_class", "1")
        }.toFields()
}
