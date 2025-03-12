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
import io.bitdrift.capture.reports.CrashReporter
import io.bitdrift.capture.reports.CrashReporter.Companion.buildFieldsMap
import io.bitdrift.capture.reports.CrashReporter.Companion.getDuration
import io.bitdrift.capture.reports.CrashReporterState
import io.bitdrift.capture.reports.CrashReporterStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CrashReporterTest {
    private lateinit var crashReporter: CrashReporter

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        crashReporter = CrashReporter(Mocks.sameThreadHandler)
    }

    @Test
    fun processCrashReportFile_withMissingConfigFile_shouldReportMissingConfigState() {
        prepareFileDirectories(doesReportsDirectoryExist = false)

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(CrashReporterState.Initialized.MissingConfigFile::class.java)
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndNotReports_shouldReportWithoutPriorCrashState() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,json",
            crashFilePresent = false,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            CrashReporterState.Initialized.WithoutPriorCrash::class.java,
        )
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndReports_shouldReportPriorCrash() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,json",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            CrashReporterState.Initialized.CrashReportSent::class.java,
        )
    }

    @Test
    fun processCrashReportFile_withInValidExtensionConfigAndReports_shouldReportPriorCrash() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "acme,yaml",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            CrashReporterState.Initialized.WithoutPriorCrash::class.java,
        )
    }

    @Test
    fun processCrashReportFile_withMalformedConfigFileAndPriorReport_shouldReportMalformedConfigFile() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "/data/crashdemo/etc",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            CrashReporterState.Initialized.MalformedConfigFile::class.java,
        )
    }

    private fun CrashReporterStatus.assert(expectedType: Class<*>) {
        assertThat(state).isInstanceOf(expectedType)
        assertThat(duration != null).isTrue()
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("_crash_reporting_state", state.readableType.toFieldValue())
                put("_crash_reporting_duration_ms", getDuration().toFieldValue())
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
