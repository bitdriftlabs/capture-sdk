// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.fakes.FakeDateProvider.DEFAULT_TEST_TIMESTAMP
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.reports.persistence.IIssueReporterStorage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IssueReporterProcessorTest {
    private lateinit var attributes: ClientAttributes
    private val reporterIssueStorage: IIssueReporterStorage = mock()
    private val streamingReportsProcessor: IStreamingReportProcessor = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val dateProvider: DateProvider = FakeDateProvider

    private lateinit var processor: IssueReporterProcessor

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        attributes = ClientAttributes(APP_CONTEXT, lifecycleOwner)
        processor =
            IssueReporterProcessor(reporterIssueStorage, attributes, streamingReportsProcessor, dateProvider)
    }

    @Test
    fun persistJavaScriptReport_withFatalIssue_shouldCallStreamingProcessorWithCorrectArguments() {
        doReturn(FAKE_FATAL_PATH).`when`(reporterIssueStorage).generateFatalIssueFilePath()

        persistJavaScriptError(isFatalIssue = true)

        assertArguments(expectedFatalIssue = true)
    }

    @Test
    fun persistJavaScriptReport_withNonFatalIssue_shouldCallStreamingProcessorWithCorrectArguments() {
        doReturn(FAKE_NON_FATAL_PATH).`when`(reporterIssueStorage).generateNonFatalIssueFilePath()

        persistJavaScriptError(isFatalIssue = false)

        assertArguments(expectedFatalIssue = false)
    }

    private fun assertArguments(expectedFatalIssue: Boolean) {
        val expectedPath = if (expectedFatalIssue) FAKE_FATAL_PATH else FAKE_NON_FATAL_PATH
        verify(streamingReportsProcessor).persistJavaScriptError(
            errorName = eq(FAKE_ERROR_NAME),
            errorMessage = eq(FAKE_ERROR_MESSAGE),
            stackTrace = eq(FAKE_STACK_TRACE),
            isFatal = eq(expectedFatalIssue),
            engine = eq(FAKE_ENGINE_JSC),
            debugId = eq(FAKE_DEBUG_ID),
            timestampMillis = eq(DEFAULT_TEST_TIMESTAMP),
            destinationPath = eq(expectedPath),
            attributes = eq(attributes),
            sdkVersion = eq(RN_BITDRIFT_VERSION),
        )
    }

    private fun persistJavaScriptError(isFatalIssue: Boolean) {
        processor.persistJavaScriptReport(
            errorName = FAKE_ERROR_NAME,
            message = FAKE_ERROR_MESSAGE,
            stack = FAKE_STACK_TRACE,
            isFatalIssue = isFatalIssue,
            engine = FAKE_ENGINE_JSC,
            debugId = FAKE_DEBUG_ID,
            sdkVersion = RN_BITDRIFT_VERSION,
        )
    }

    private companion object {
        const val FAKE_FATAL_PATH = "/reports/new/fatal-report.cap"
        const val FAKE_NON_FATAL_PATH = "/reports/watcher/current_session/non-fatal-report.cap"
        const val FAKE_ERROR_NAME = "TestError"
        const val FAKE_ERROR_MESSAGE = "Test error message"
        const val FAKE_STACK_TRACE = "at testFunction (test.js:1:1)"
        const val FAKE_ENGINE_JSC = "jsc"
        const val FAKE_DEBUG_ID = "test-debugger-id-12345"

        const val RN_BITDRIFT_VERSION = "8.1"
    }
}
