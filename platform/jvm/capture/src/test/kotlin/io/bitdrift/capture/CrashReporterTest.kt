package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.reports.CrashReporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun processCrashReportFile_withMissingConfigFile_shouldReportConfigState() {
        val crashReporter = crashReporter.processCrashReportFile()

        assertThat(crashReporter).isInstanceOf(CrashReporter.CrashReportingState.Completed.MissingConfigFile::class.java)
    }
}
