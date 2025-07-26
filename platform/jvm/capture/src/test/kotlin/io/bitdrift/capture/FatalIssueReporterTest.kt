// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.buildFieldsMap
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.getDuration
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.FatalIssueReporterStatus
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // needs API 30 to use ApplicationExitInfo
class FatalIssueReporterTest {
    private lateinit var fatalIssueReporter: FatalIssueReporter
    private lateinit var reportsDir: File
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val clientAttributes = ClientAttributes(appContext, lifecycleOwner)

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        reportsDir = File(APP_CONTEXT.filesDir, "bitdrift_capture/reports/")
        fatalIssueReporter = buildReporter()
    }

    @Test
    fun initialize_whenBuiltInMechanism_shouldInitCrashHandlerAndFetchAppExitReason() {
        fatalIssueReporter.initBuiltInMode(appContext, clientAttributes)

        verify(captureUncaughtExceptionHandler).install(eq(fatalIssueReporter))
        verify(latestAppExitInfoProvider).get(any())
        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.BuiltIn::class.java,
        )
    }

    private fun FatalIssueReporterStatus.assert(
        expectedType: Class<*>,
        crashFileExist: Boolean = false,
    ) {
        assertThat(state).isInstanceOf(expectedType)
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("_fatal_issue_reporting_duration_ms", getDuration().toFieldValue())
                put("_fatal_issue_reporting_state", state.readableType.toFieldValue())
            }
        assertThat(duration).isNotNull()
        assertThat(buildFieldsMap()).isEqualTo(expectedMap)
        assertCrashFile(crashFileExist)
    }

    private fun assertCrashFile(crashFileExist: Boolean) {
        val crashFile = File(reportsDir, "/new/latest_crash_info.json")
        assertThat(crashFile.exists()).isEqualTo(crashFileExist)
    }

    private fun buildReporter(): FatalIssueReporter =
        FatalIssueReporter(
            latestAppExitInfoProvider,
            captureUncaughtExceptionHandler,
        )
}
