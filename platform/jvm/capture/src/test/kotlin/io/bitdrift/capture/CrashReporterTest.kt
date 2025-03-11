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
import io.bitdrift.capture.reports.CrashReporter.Companion.getDurationFieldValue
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Completed.CrashReportSent
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Completed.MalformedConfigFile
import io.bitdrift.capture.reports.CrashReporter.CrashReporterState.Completed.WithoutPriorCrash
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
            CrashReporter.CrashReporterState.Completed.MissingConfigFile::class.java,
            "/bitdrift_capture/reports/directories does not exist",
        )
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndNotReports_shouldReportWithoutPriorCrashState() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfig = "acme,json",
            crashFilePresent = false,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            WithoutPriorCrash::class.java,
            "io.bitdrift.capture-dataDir/cache/acme directory does not exist or is not a directory",
        )
    }

    @Test
    fun processCrashReportFile_withValidConfigFileAndReports_shouldReportPriorCrash() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfig = "acme,json",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            CrashReportSent::class.java,
            ".json copied successfully",
        )
    }

    @Test
    fun processCrashReportFile_withInValidExtensionConfigAndReports_shouldReportPriorCrash() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfig = "acme,yaml",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            WithoutPriorCrash::class.java,
            "Crash file with .yaml extension not found in the source directory",
        )
    }

    @Test
    fun processCrashReportFile_withMalformedConfigFileAndPriorReport_shouldReportMalformedConfigFile() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfig = "/data/crashdemo/etc",
            crashFilePresent = true,
        )

        val crashReporterStatus = crashReporter.processCrashReportFile()

        crashReporterStatus.assert(
            MalformedConfigFile::class.java,
            "Malformed content at /bitdrift_capture/reports/directories",
        )
    }

    private fun CrashReporterStatus.assert(
        expectedType: Class<*>,
        expectedMessage: String,
    ) {
        assertThat(state).isInstanceOf(expectedType)
        assertThat(state.message).contains(expectedMessage)
        assertThat(duration != null).isTrue()
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("crash_reporting_state", state.readableType.toFieldValue())
                put("crash_reporting_details", state.message.toFieldValue())
                put("crash_reporting_duration_nanos", getDurationFieldValue().toFieldValue())
            }
        assertThat(buildFieldsMap()).isEqualTo(expectedMap)
    }

    private fun prepareFileDirectories(
        doesReportsDirectoryExist: Boolean,
        bitdriftConfig: String? = null,
        crashFilePresent: Boolean = false,
    ) {
        if (doesReportsDirectoryExist) {
            val filesDir = APP_CONTEXT.filesDir
            val reportsDir = File(filesDir, "bitdrift_capture/reports/")
            reportsDir.mkdirs()
            val reportFile = File(reportsDir, "directories")
            bitdriftConfig?.let {
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
