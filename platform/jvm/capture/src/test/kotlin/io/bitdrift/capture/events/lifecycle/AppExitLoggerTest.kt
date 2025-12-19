// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.LogAttributesOverrides
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.fakes.FakeFatalIssueReporter
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.FAKE_EXCEPTION
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.TIME_STAMP
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider.Companion.DEFAULT_MEMORY_ATTRIBUTES
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_EXCEPTION_MESSAGE
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.utils.BuildVersionChecker
import io.bitdrift.capture.utils.toStringMap
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.verifyNoMoreInteractions
import java.io.IOException

class AppExitLoggerTest {
    private val logger: LoggerImpl = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()

    private val errorHandler: ErrorHandler = mock()
    private val versionChecker: BuildVersionChecker = mock()
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()
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
    fun logPreviousExitReasonIfAny_withValidReason_shouldEmitAppExitLog() {
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
            argThat<ArrayFields> { fields -> fieldsMatchExpectedAnr(fields) },
            eq(ArrayFields.EMPTY),
            eq(LogAttributesOverrides.PreviousRunSessionId(TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
    }

    @Test
    fun logPreviousExitReason_whenEmptyResult_shouldNotReportError() {
        // ARRANGE
        lastExitInfo.setAsEmptyReason()

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        verifyNoMoreInteractions(errorHandler)
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
    fun logPreviousExitReason_whenErrorResult_shouldReportError() {
        // ARRANGE
        lastExitInfo.setAsErrorResult()

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        val messageCaptor = argumentCaptor<String>()
        val throwableCaptor = argumentCaptor<Throwable>()
        verify(errorHandler).handleError(messageCaptor.capture(), throwableCaptor.capture())
        assert(messageCaptor.firstValue == EXIT_REASON_EXCEPTION_MESSAGE)
        assert(throwableCaptor.firstValue is Exception)
        assert(throwableCaptor.firstValue.message == FAKE_EXCEPTION.message)

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
            combineFields(
                fieldsOf(
                    "_app_exit_source" to "UncaughtExceptionHandler",
                    "_app_exit_reason" to "Crash",
                    "_app_exit_info" to appException.javaClass.name,
                    "_app_exit_details" to appException.message.orEmpty(),
                    "_app_exit_thread" to currentThread.name,
                ),
                DEFAULT_MEMORY_ATTRIBUTES,
            )

        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            argThat<ArrayFields> { fields -> fieldsMatchExpected(fields, expectedFields) },
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(true),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
        verify(logger).flush(true)
    }

    @Test
    fun onJvmCrash_whenBuiltInFatalIssueMechanism_shouldNotSendAppExitCrashLog() {
        val appExitLogger = buildAppExitLogger(FatalIssueReporterState.Initialized)
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

    @Test
    fun onJvmCrash_withLinearExceptionChain_shouldLogRootCause() {
        val appExitLogger = buildAppExitLogger()
        val rootCauseException = IllegalArgumentException("root cause")
        val middleException = RuntimeException("middle", rootCauseException)
        val topException = IllegalStateException("top", middleException)

        appExitLogger.onJvmCrash(Thread.currentThread(), topException)

        verifyLoggedException(rootCauseException)
    }

    @Test
    fun onJvmCrash_withExceptionCycle_shouldNotInfiniteLoop() {
        val appExitLogger = buildAppExitLogger()
        val rootCauseException = IllegalArgumentException("root")
        val middleException = RuntimeException("middle", rootCauseException)
        val topException = IllegalStateException("top", middleException)
        // rootCause -> middle -> top -> rootCause
        createExceptionCycle(rootCauseException, topException)

        appExitLogger.onJvmCrash(Thread.currentThread(), topException)

        verifyLoggedException(topException)
    }

    @Test
    fun onJvmCrash_withSelfCycle_shouldNotInfiniteLoop() {
        val appExitLogger = buildAppExitLogger()
        val initialException = RuntimeException("Initial exception")
        createExceptionCycle(initialException, initialException)

        appExitLogger.onJvmCrash(Thread.currentThread(), initialException)

        verifyLoggedException(initialException)
    }

    @Test
    fun onJvmCrash_withInvocationTargetException_shouldReportIllegalArgException() {
        val appExitLogger = buildAppExitLogger()
        val illegalArgumentException = IllegalArgumentException("root cause")
        val invocationTarget = java.lang.reflect.InvocationTargetException(illegalArgumentException)
        val top = RuntimeException("top", invocationTarget)

        appExitLogger.onJvmCrash(Thread.currentThread(), top)

        verifyLoggedException(illegalArgumentException)
    }

    private fun verifyLoggedException(expected: Throwable) {
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            argThat<ArrayFields> { fields ->
                fields["_app_exit_info"] == expected.javaClass.name &&
                    fields["_app_exit_details"] == expected.message.orEmpty()
            },
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(true),
            any(),
        )
    }

    private fun createExceptionCycle(
        target: Throwable,
        cause: Throwable,
    ) {
        val causeField = Throwable::class.java.getDeclaredField("cause")
        causeField.isAccessible = true
        causeField.set(target, cause)
    }

    private fun buildAppExitLogger(fatalReporterInitState: FatalIssueReporterState = FatalIssueReporterState.NotInitialized) =
        AppExitLogger(
            logger,
            activityManager,
            runtime,
            errorHandler,
            versionChecker,
            memoryMetricsProvider,
            lastExitInfo,
            captureUncaughtExceptionHandler,
            FakeFatalIssueReporter(fatalReporterInitState),
        )

    private fun fieldsMatchExpectedAnr(arrayFields: ArrayFields): Boolean {
        val expectedKeys =
            setOf(
                "_app_exit_source",
                "_app_exit_process_name",
                "_app_exit_reason",
                "_app_exit_importance",
                "_app_exit_status",
                "_app_exit_pss",
                "_app_exit_rss",
                "_app_exit_description",
                "_memory_class",
            )
        return arrayFields.keys.toSet().containsAll(expectedKeys) &&
            arrayFields["_app_exit_source"] == "ApplicationExitInfo" &&
            arrayFields["_app_exit_reason"] == "ANR"
    }

    private fun fieldsMatchExpected(
        actual: ArrayFields,
        expected: ArrayFields,
    ): Boolean = actual.toStringMap() == expected.toStringMap()
}
