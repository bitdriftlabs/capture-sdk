// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.utils.CacheFormattingError
import io.bitdrift.capture.utils.SdkDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // needs API 30 to use ApplicationExitInfo
class FatalIssueReporterTest {
    private lateinit var fatalIssueReporter: FatalIssueReporter
    private lateinit var reportsDir: File
    private lateinit var configFile: File

    private lateinit var sdkDirectory: String
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val completedReportsProcessor: ICompletedReportsProcessor = mock()

    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val clientAttributes = ClientAttributes(appContext, lifecycleOwner)

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        reportsDir = File(APP_CONTEXT.filesDir, "bitdrift_capture/reports/").apply { if (!exists()) mkdirs() }
        configFile = File(reportsDir, "config.csv")
        configFile.writeText("crash_reporting.enabled,true")
        sdkDirectory = SdkDirectory.getPath(APP_CONTEXT)
        fatalIssueReporter = buildReporter()
    }

    @After
    fun teardown() {
        reportsDir.delete()
    }

    @Test
    fun initialize_whenDisabledViaConfig_shouldNotInit() {
        configFile.writeText("crash_reporting.enabled,false")
        fatalIssueReporter.init(appContext, sdkDirectory, clientAttributes, completedReportsProcessor)

        fatalIssueReporter.fatalIssueReporterState.assert(
            FatalIssueReporterState.RuntimeDisabled::class.java,
        )
        assertThat(
            fatalIssueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun initialize_whenConfigCorrupt_shouldNotInit() {
        configFile.writeText("crash_reporting.enabled")
        fatalIssueReporter.init(appContext, sdkDirectory, clientAttributes, completedReportsProcessor)

        fatalIssueReporter.fatalIssueReporterState.assert(
            FatalIssueReporterState.RuntimeInvalid::class.java,
        )
        assertThat(
            fatalIssueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull

        verify(completedReportsProcessor).onReportProcessingError(
            any(),
            isA<CacheFormattingError>(),
        )
    }

    @Test
    fun initialize_whenConfigNotPresent_shouldNotInit() {
        configFile.delete()
        fatalIssueReporter.init(appContext, sdkDirectory, clientAttributes, completedReportsProcessor)

        fatalIssueReporter.fatalIssueReporterState.assert(
            FatalIssueReporterState.RuntimeUnset::class.java,
        )
        assertThat(
            fatalIssueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull

        verify(completedReportsProcessor).onReportProcessingError(
            any(),
            isA<IOException>(),
        )
    }

    @Test
    fun initialize_whenEnabled_shouldInitCrashHandlerAndFetchAppExitReason() {
        fatalIssueReporter.init(appContext, sdkDirectory, clientAttributes, completedReportsProcessor)

        verify(captureUncaughtExceptionHandler).install(eq(fatalIssueReporter))
        verify(latestAppExitInfoProvider).get(any())
        fatalIssueReporter.fatalIssueReporterState.assert(
            FatalIssueReporterState.Initialized::class.java,
        )
        verify(completedReportsProcessor).processCrashReports()
    }

    private fun FatalIssueReporterState.assert(
        expectedType: Class<*>,
        crashFileExist: Boolean = false,
    ) {
        assertThat(this).isInstanceOf(expectedType)
        assertCrashFile(crashFileExist)
    }

    @Test
    fun init_whenAppExitInfoFails_shouldCallOnErrorOccurred() {
        val exception = RuntimeException("test error")
        whenever(latestAppExitInfoProvider.get(any()))
            .thenThrow(exception)

        fatalIssueReporter.init(appContext, sdkDirectory, clientAttributes, completedReportsProcessor)

        verify(completedReportsProcessor).onReportProcessingError(
            any(),
            eq(exception),
        )
    }

    private fun assertCrashFile(crashFileExist: Boolean) {
        val crashFile = File(reportsDir, "/new/latest_crash_info.json")
        assertThat(crashFile.exists()).isEqualTo(crashFileExist)
    }

    private fun buildReporter(): FatalIssueReporter =
        FatalIssueReporter(
            enableNativeCrashReporting = true,
            FakeBackgroundThreadHandler(),
            latestAppExitInfoProvider,
            captureUncaughtExceptionHandler,
        )
}
