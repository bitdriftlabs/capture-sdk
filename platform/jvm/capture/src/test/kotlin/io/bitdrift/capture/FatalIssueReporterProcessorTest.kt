// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.createTraceInputStream
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class FatalIssueReporterProcessorTest {
    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage = mock()
    private val fatalIssueReportCaptor = argumentCaptor<FatalIssueReport>()

    @Before
    fun setUp() {
        fatalIssueReporterProcessor = FatalIssueReporterProcessor(fatalIssueReporterStorage)
    }

    @Test
    fun persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel() {
        val callerThread = Thread("crashing_thread")
        val fakeException = FakeJvmException()

        fatalIssueReporterProcessor.persistJvmCrash(
            FAKE_TIME_STAMP,
            callerThread,
            fakeException,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            eq(FatalIssueType.JVM_CRASH),
            fatalIssueReportCaptor.capture(),
        )
        assertThat(fatalIssueReportCaptor.firstValue.type).isEqualTo(FatalIssueType.JVM_CRASH)
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].reason).isEqualTo("Fake JVM exception")
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].name).isEqualTo("io.bitdrift.capture.fakes.FakeJvmException")
    }

    @Test
    fun persistAppExitReport_whenAnr_shouldCreateNonEmptyErrorModel() {
        val description =
            "Input dispatching timed out (219180 " +
                "io.bitdrift.gradletestapp/io.bitdrift.gradletestapp.MainActivity (server) " +
                "is not responding. Waited 5004ms for MotionEvent"
        val traceInputStream = createTraceInputStream("sample anr trace")

        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = FatalIssueType.ANR,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            eq(FatalIssueType.ANR),
            fatalIssueReportCaptor.capture(),
        )
        assertThat(fatalIssueReportCaptor.firstValue.type).isEqualTo(FatalIssueType.ANR)
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].reason).isEqualTo("ANR reason wip")
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].name).isEqualTo(description)
    }

    @Test
    fun persistAppExitReport_whenNativeCrash_shouldCreateEmptyErrorModel() {
        val description = "Native crash"
        val traceInputStream = createTraceInputStream("sample native crash trace")

        fatalIssueReporterProcessor.persistAppExitReport(
            FatalIssueType.NATIVE_CRASH,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            eq(FatalIssueType.NATIVE_CRASH),
            fatalIssueReportCaptor.capture(),
        )
        assertThat(fatalIssueReportCaptor.firstValue.type).isEqualTo(FatalIssueType.NATIVE_CRASH)
        assertThat(fatalIssueReportCaptor.firstValue.errors.isEmpty()).isTrue()
    }

    @Test
    fun persistAppExitReport_withInvalidReason_shouldNotInteractWithStorage() {
        val description = null
        val traceInputStream = createTraceInputStream("sample native crash trace")

        fatalIssueReporterProcessor.persistAppExitReport(
            FatalIssueType.JVM_CRASH,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage, never())
            .persistFatalIssue(any(), any(), any())
    }

    private companion object {
        const val FAKE_TIME_STAMP = 1241515210914L
    }
}
