// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import io.bitdrift.capture.Configuration
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FatalIssueReporterFactoryTest {
    @Test
    fun create_withFatalIssueReportingDisabled_shouldReturnNull() {
        val configuration =
            Configuration(
                enableFatalIssueReporting = false,
                enableNativeCrashReporting = false,
            )

        val reporter = FatalIssueReporterFactory.create(configuration)

        assertThat(reporter).isNull()
    }

    @Test
    fun create_withFatalIssueReportingDisabledAndNativeCrashEnabled_shouldReturnNull() {
        val configuration =
            Configuration(
                enableFatalIssueReporting = false,
                enableNativeCrashReporting = true,
            )

        val reporter = FatalIssueReporterFactory.create(configuration)

        assertThat(reporter).isNull()
    }

    @Test
    fun create_withFatalIssueReportingEnabledAndNativeCrashDisabled_shouldReturnReporter() {
        val configuration =
            Configuration(
                enableFatalIssueReporting = true,
                enableNativeCrashReporting = false,
            )

        val reporter = FatalIssueReporterFactory.create(configuration)

        assertThat(reporter).isInstanceOf(FatalIssueReporter::class.java)
    }

    @Test
    fun create_withBothFatalIssueReportingAndNativeCrashEnabled_shouldReturnReporter() {
        val configuration =
            Configuration(
                enableFatalIssueReporting = true,
                enableNativeCrashReporting = true,
            )

        val reporter = FatalIssueReporterFactory.create(configuration)

        assertThat(reporter).isInstanceOf(FatalIssueReporter::class.java)
    }
}
