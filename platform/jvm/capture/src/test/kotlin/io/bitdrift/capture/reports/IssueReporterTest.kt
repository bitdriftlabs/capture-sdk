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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.events.performance.MemoryMetricsProvider
import io.bitdrift.capture.events.performance.MemoryPressureLevel
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.IssueReporterProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.utils.SdkDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // needs API 30 to use ApplicationExitInfo
class IssueReporterTest {
    private lateinit var reportsDir: File
    private lateinit var configFile: File

    private lateinit var sdkDirectory: String
    private val lifecycleOwner: LifecycleOwner = mock()

    private val completedReportsProcessor: ICompletedReportsProcessor = mock()

    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()

    private val internalLogger: IInternalLogger = mock()
    private val memoryMetricsProvider: MemoryMetricsProvider = mock()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val clientAttributes = ClientAttributes(appContext, lifecycleOwner)

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        reportsDir =
            File(
                APP_CONTEXT.filesDir,
                "bitdrift_capture/reports/",
            ).apply { if (!exists()) mkdirs() }
        configFile = File(reportsDir, "config.csv")
        configFile.writeText("crash_reporting.enabled,true")
        sdkDirectory = SdkDirectory.getPath(APP_CONTEXT)
    }

    @After
    fun teardown() {
        reportsDir.delete()
    }

    @Test
    fun construction_whenDisabledViaConfig_shouldNotHandleJvmCrashes() {
        configFile.writeText("crash_reporting.enabled,false")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Disabled::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun construction_whenConfigCorrupt_shouldNotHandleJvmCrashes() {
        configFile.writeText("crash_reporting.enabled")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Invalid::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun construction_whenConfigMissingCrashReportingKey_shouldNotHandleJvmCrashes() {
        configFile.writeText("other.flag,true")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.MissingFlag::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun construction_whenConfigCrashReportingValueIsNotBoolean_shouldNotHandleJvmCrashes() {
        configFile.writeText("crash_reporting.enabled,not-a-bool")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.MissingFlag::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun construction_whenConfigFileIsEmpty_shouldBeInvalid() {
        configFile.writeText("")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Invalid::class.java,
        )
    }

    @Test
    fun construction_whenConfigHasMultipleKeysButMissingCrashReporting_shouldBeMissingFlag() {
        configFile.writeText("anr_reporting.enabled,true\nother.flag,false")

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isFalse
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.MissingFlag::class.java,
        )
    }

    @Test
    fun construction_whenConfigNotPresent_shouldDefaultToEnabled() {
        configFile.delete()

        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isTrue
        issueReporter.issueReporterState.assert(
            IssueReporterState.Initializing::class.java,
        )
    }

    @Test
    fun construction_whenEnabled_shouldHandleJvmCrashesBeforePriorReportsAreProcessed() {
        val issueReporter = buildReporter()

        assertThat(issueReporter.handlesJvmCrashes).isTrue
        issueReporter.issueReporterState.assert(
            IssueReporterState.Initializing::class.java,
        )
    }

    @Test
    fun processPriorReports_whenEnabled_shouldFetchAppExitReasonAndInitialize() {
        val issueReporter = buildReporter()

        issueReporter.processPriorReports(completedReportsProcessor)

        verify(latestAppExitInfoProvider).get()
        issueReporter.issueReporterState.assert(
            IssueReporterState.Initialized::class.java,
        )
        verify(completedReportsProcessor).processIssueReports(ReportProcessingSession.PreviousRun)
    }

    @Test
    fun processPriorReports_whenDisabledViaConfig_shouldNotProcessReports() {
        configFile.writeText("crash_reporting.enabled,false")
        val issueReporter = buildReporter()

        issueReporter.processPriorReports(completedReportsProcessor)

        verify(completedReportsProcessor, times(0)).processIssueReports(any())
        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Disabled::class.java,
        )
    }

    @Test
    fun processPriorReports_whenCalledTwice_shouldOnlyProcessOnce() {
        val issueReporter = buildReporter()

        issueReporter.processPriorReports(completedReportsProcessor)
        issueReporter.processPriorReports(completedReportsProcessor)

        verify(completedReportsProcessor, times(1)).processIssueReports(ReportProcessingSession.PreviousRun)
    }

    @Test
    fun processPriorReports_whenAppExitInfoFails_shouldCallOnErrorOccurred() {
        val exception = RuntimeException("test error")
        whenever(latestAppExitInfoProvider.get())
            .thenThrow(exception)
        val issueReporter = buildReporter()

        issueReporter.processPriorReports(completedReportsProcessor)

        verify(completedReportsProcessor).onReportProcessingError(
            any(),
            eq(exception),
        )
        issueReporter.issueReporterState.assert(
            IssueReporterState.InitializationFailed::class.java,
        )
    }

    @Test
    fun getIssueReporterProcessor_whenDisabledViaConfig_shouldReturnNull() {
        configFile.writeText("crash_reporting.enabled,false")

        val processor = buildReporter().getIssueReporterProcessor()

        assertThat(processor).isNull()
    }

    @Test
    fun getIssueReporterProcessor_whenEnabled_shouldReturnValidProcessorFromConstruction() {
        val processor = buildReporter().getIssueReporterProcessor()

        assertThat(processor).isInstanceOf(IssueReporterProcessor::class.java)
    }

    @Test
    fun onJvmCrash_whenEnabled_shouldUpdateMemoryLevelState() {
        whenever(memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()).thenReturn(MemoryPressureLevel.Critical)
        val issueReporter = buildReporter()

        issueReporter.onJvmCrash(Thread(), IllegalStateException())

        verify(internalLogger).notifyMemoryPressureLevel(MemoryPressureLevel.Critical)
    }

    private fun IssueReporterState.assert(expectedType: Class<*>) {
        assertThat(this).isInstanceOf(expectedType)
    }

    private fun buildReporter(): IssueReporter =
        IssueReporter(
            internalLogger = internalLogger,
            backgroundThreadHandler = FakeBackgroundThreadHandler(),
            latestAppExitInfoProvider = latestAppExitInfoProvider,
            dateProvider = FakeDateProvider,
            memoryMetricsProvider = memoryMetricsProvider,
            sdkDirectory = sdkDirectory,
            clientAttributes = clientAttributes,
            loggerId = TEST_LOGGER_ID,
        )

    private companion object {
        const val TEST_LOGGER_ID = 1L
    }
}
