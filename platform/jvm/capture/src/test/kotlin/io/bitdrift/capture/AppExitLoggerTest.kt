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
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.AppExitLogger
import io.bitdrift.capture.events.lifecycle.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider.Companion.DEFAULT_MEMORY_ATTRIBUTES_MAP
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AppExitLoggerTest {
    private val logger: LoggerImpl = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()

    private val errorHandler: ErrorHandler = mock()
    private val crashHandler: CaptureUncaughtExceptionHandler = mock()
    private val versionChecker: BuildVersionChecker = mock()
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()
    private val backgroundThreadHandler = FakeBackgroundThreadHandler()
    private lateinit var appExitLogger: AppExitLogger

    @Before
    fun setUp() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(true)
        whenever(versionChecker.isAtLeast(anyInt())).thenReturn(true)
        appExitLogger =
            AppExitLogger(
                logger,
                activityManager,
                runtime,
                errorHandler,
                crashHandler,
                versionChecker,
                memoryMetricsProvider,
                backgroundThreadHandler,
            )
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
            buildMap {
                put("_app_exit_source", "ApplicationExitInfo")
                put("_app_exit_process_name", "test-process-name")
                put("_app_exit_reason", "ANR")
                put("_app_exit_importance", "FOREGROUND")
                put("_app_exit_status", "0")
                put("_app_exit_pss", "1")
                put("_app_exit_rss", "2")
                put("_app_exit_description", "test-description")
                putAll(DEFAULT_MEMORY_ATTRIBUTES_MAP)
            }.toFields()
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
    fun logPreviousExitReasonIfAny_whenCrashNativeAndEmptyTrace_shouldNotAddCrashArtifactMetadata() {
        // ARRANGE
        setupMockRuntimeAndExitData(
            isCrashArtifactEnabled = true,
            exitReason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = null,
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenCrashNative_shouldAddCrashArtifactMetadata() {
        // ARRANGE
        val traceInputStream = createTestInputStream()
        setupMockRuntimeAndExitData(
            isCrashArtifactEnabled = true,
            exitReason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = traceInputStream,
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenCrashNativeValidTombstoneAndKillSwitch_shouldNotAddCrashArtifactMetadata() {
        // ARRANGE
        val traceInputStream = createTestInputStream()
        setupMockRuntimeAndExitData(
            isCrashArtifactEnabled = false,
            exitReason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = traceInputStream,
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenAnr_shouldNotAddCrashArtifactMetadata() {
        // ARRANGE
        val traceInputStream = createTestInputStream()
        setupMockRuntimeAndExitData(
            isCrashArtifactEnabled = false,
            exitReason = ApplicationExitInfo.REASON_ANR,
            traceInputStream = traceInputStream,
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
    }

    private fun setupMockRuntimeAndExitData(
        isCrashArtifactEnabled: Boolean,
        exitReason: Int,
        traceInputStream: InputStream?,
    ) {
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(isCrashArtifactEnabled)
        mockAppExitData(exitReason, traceInputStream)
    }

    private fun mockAppExitData(
        exitReason: Int,
        traceInputStream: InputStream? = null,
    ) {
        val mockExitInfo = mock<ApplicationExitInfo>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockExitInfo.processStateSummary).thenReturn(SESSION_ID.toByteArray(StandardCharsets.UTF_8))
        whenever(mockExitInfo.timestamp).thenReturn(TIME_STAMP)
        whenever(mockExitInfo.processName).thenReturn("test-process-name")
        whenever(mockExitInfo.reason).thenReturn(exitReason)
        whenever(mockExitInfo.importance).thenReturn(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        whenever(mockExitInfo.status).thenReturn(0)
        whenever(mockExitInfo.pss).thenReturn(1)
        whenever(mockExitInfo.rss).thenReturn(2)
        whenever(mockExitInfo.description).thenReturn("test-description")
        whenever(mockExitInfo.traceInputStream).thenReturn(traceInputStream)
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))
    }

    private fun assertCrashArtifactAdded() {
        val expectedFieldsCaptor = argumentCaptor<InternalFieldsMap>()
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            expectedFieldsCaptor.capture(),
            eq(null),
            eq(LogAttributesOverrides(SESSION_ID, TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
        val fieldValues = expectedFieldsCaptor.firstValue
        assertThat(fieldValues).containsKey("_crash_artifact")
        assertThat(fieldValues["_crash_artifact"]).isEqualTo(FAKE_CRASH_STACKTRACE.toByteArray().toFieldValue())
    }

    private fun assertCrashArtifactNotAdded() {
        val expectedFieldsCaptor = argumentCaptor<InternalFieldsMap>()
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.ERROR),
            expectedFieldsCaptor.capture(),
            eq(null),
            eq(LogAttributesOverrides(SESSION_ID, TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
        assertThat(expectedFieldsCaptor.firstValue).doesNotContainKey("_crash_artifact")
    }

    private fun createTestInputStream(): InputStream = ByteArrayInputStream(FAKE_CRASH_STACKTRACE.toByteArray(Charsets.UTF_8))

    private companion object {
        private const val SESSION_ID = "test-session-id"
        private const val FAKE_CRASH_STACKTRACE = "SIG crash"
        private const val TIME_STAMP = 123L
    }
}
