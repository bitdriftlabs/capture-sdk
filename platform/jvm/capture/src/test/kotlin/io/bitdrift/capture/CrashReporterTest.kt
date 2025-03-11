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
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Initialized.CrashReportSent
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Initialized.MalformedConfigFile
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Initialized.MissingConfigFile
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Initialized.WithoutPriorCrash
import io.bitdrift.capture.reports.CrashReporter.CrashReporterStatus
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

        crashReporterStatus.assert(
            MissingConfigFile::class.java,
            "Configuration file does not exits",
        )
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
            WithoutPriorCrash::class.java,
            "Prior crash not found",
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
            CrashReportSent::class.java,
            "Crash file copied successfully",
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
            WithoutPriorCrash::class.java,
            "Crash file not found in the source directory",
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
            MalformedConfigFile::class.java,
            "Malformed content at configuration file",
        )
    }

    private fun CrashReporterStatus.assert(
        expectedType: Class<*>,
        expectedMessage: String,
    ) {
        assertThat(state).isInstanceOf(expectedType)
        assertThat(state.message).isEqualTo(expectedMessage)
        assertThat(duration != null).isTrue()
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("_crash_reporting_state", state.readableType.toFieldValue())
                put("_crash_reporting_details", state.message.toFieldValue())
                put("_crash_reporting_duration_nanos", getDuration().toFieldValue())
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
            val reportFile = File(reportsDir, "directories")
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
