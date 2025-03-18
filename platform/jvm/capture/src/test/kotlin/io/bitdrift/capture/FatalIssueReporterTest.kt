// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.buildFieldsMap
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.getDuration
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.FatalIssueReporterStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class FatalIssueReporterTest {
    private lateinit var fatalIssueReporter: FatalIssueReporter

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        fatalIssueReporter = FatalIssueReporter(Mocks.sameThreadHandler)
    }

    @Test
    fun processPriorReportFile_withMissingConfigFile_shouldReportMissingConfigState() {
        prepareFileDirectories(doesReportsDirectoryExist = false)

        val crashReporterStatus = fatalIssueReporter.processPriorReportFiles()

        crashReporterStatus.assert(FatalIssueReporterState.Initialized.MissingConfigFile::class.java)
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndNotReports_shouldReportWithoutPriorPriorState() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,json",
            crashFilePresent = false,
        )

        val crashReporterStatus = fatalIssueReporter.processPriorReportFiles()

        crashReporterStatus.assert(
            FatalIssueReporterState.Initialized.WithoutPriorFatalIssue::class.java,
        )
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndReports_shouldReportPriorPrior() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,json",
            crashFilePresent = true,
        )

        val crashReporterStatus = fatalIssueReporter.processPriorReportFiles()

        crashReporterStatus.assert(
            FatalIssueReporterState.Initialized.FatalIssueReportSent::class.java,
        )
    }

    @Test
    fun processCrashReportFile_withInValidExtensionConfigAndReports_shouldReportPriorPrior() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,yaml",
            crashFilePresent = true,
        )

        val crashReporterStatus = fatalIssueReporter.processPriorReportFiles()

        crashReporterStatus.assert(
            FatalIssueReporterState.Initialized.WithoutPriorFatalIssue::class.java,
        )
    }

    @Test
    fun processPriorReportFile_withMalformedConfigFileAndPriorReport_shouldReportMalformedConfigFiles() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "/data/crashdemo/etc",
            crashFilePresent = true,
        )

        val crashReporterStatus = fatalIssueReporter.processPriorReportFiles()

        crashReporterStatus.assert(
            FatalIssueReporterState.Initialized.MalformedConfigFile::class.java,
        )
    }

    private fun FatalIssueReporterStatus.assert(expectedType: Class<*>) {
        assertThat(state).isInstanceOf(expectedType)
        assertThat(duration != null).isTrue()
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("_fatal_issue_reporting_duration_ms", getDuration().toFieldValue())
                put("_fatal_issue_reporting_state", state.readableType.toFieldValue())
            }
        assertThat(buildFieldsMap()).isEqualTo(expectedMap)
    }

    private fun prepareFileDirectories(
        doesReportsDirectoryExist: Boolean,
        bitdriftConfigContent: String? = null,
        crashFilePresent: Boolean = false,
    ) {
        if (doesReportsDirectoryExist) {
            val filesDir = APP_CONTEXT.filesDir
            val reportsDir = File(filesDir, "bitdrift_capture/reports/")
            reportsDir.mkdirs()
            val reportFile = File(reportsDir, "config")
            bitdriftConfigContent?.let {
                reportFile.writeText(it)
            }
        }

        if (crashFilePresent) {
            val cacheDir = APP_CONTEXT.cacheDir
            val sourceFileDir = File(cacheDir, "acme")
            sourceFileDir.mkdirs()
            val sourceFile = File(sourceFileDir, "crash_info.json")
            sourceFile.createNewFile()
        }
    }
}
