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
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.SESSION_ID
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.TIME_STAMP
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider.Companion.DEFAULT_MEMORY_ATTRIBUTES_MAP
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
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
    private val lastExitInfo = FakeLatestAppExitInfoProvider()

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
                lastExitInfo,
            )
        lastExitInfo.reset()
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
        lastExitInfo.set(
            exitReasonType = ApplicationExitInfo.REASON_ANR,
            description = "test-description",
        )

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
            eq(LogAttributesOverrides(SESSION_ID, TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
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
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(true)
        lastExitInfo.set(exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE)

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenCrashNative_shouldAddCrashArtifactMetadata() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(true)
        lastExitInfo.set(
            exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = createValidInputStream(),
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenCrashNativeValidTombstoneAndKillSwitch_shouldNotAddCrashArtifactMetadata() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(false)
        lastExitInfo.set(
            exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = createValidInputStream(),
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
    }

    @Test
    fun logPreviousExitReasonIfAny_whenCrashNative_shouldNotAddCrashArtifactMetadataAndLogError() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(true)
        lastExitInfo.set(
            exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE,
            traceInputStream = createInvalidInputStream(),
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded()
        verify(errorHandler).handleError("Couldn't convert TraceInputStream to ByteArray", INPUT_STREAM_EXCEPTION)
    }

    @Test
    fun logPreviousExitReasonIfAny_whenAnr_shouldNotAddCrashArtifactMetadata() {
        // ARRANGE
        whenever(runtime.isEnabled(RuntimeFeature.SEND_CRASH_ARTIFACT)).thenReturn(true)
        lastExitInfo.set(
            exitReasonType = ApplicationExitInfo.REASON_ANR,
            traceInputStream = createValidInputStream(),
        )

        // ACT
        appExitLogger.logPreviousExitReasonIfAny()

        // ASSERT
        assertCrashArtifactNotAdded(exitReasonText = "ANR")
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

    private fun assertCrashArtifactNotAdded(exitReasonText: String = "CRASH_NATIVE") {
        val expectedFields =
            buildMap {
                put("_app_exit_source", "ApplicationExitInfo")
                put("_app_exit_process_name", "test-process-name")
                put("_app_exit_reason", exitReasonText)
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
            eq(LogAttributesOverrides(SESSION_ID, TIME_STAMP)),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppExit" },
        )
    }

    private fun createValidInputStream(): InputStream = ByteArrayInputStream(FAKE_CRASH_STACKTRACE.toByteArray(Charsets.UTF_8))

    private fun createInvalidInputStream(): InputStream =
        object : InputStream() {
            override fun read(): Int = throw INPUT_STREAM_EXCEPTION
        }

    private companion object {
        private const val FAKE_CRASH_STACKTRACE = "SIG crash"
        private val INPUT_STREAM_EXCEPTION = IllegalArgumentException("Invalid size")
    }
}
