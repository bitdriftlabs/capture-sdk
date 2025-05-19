// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.createTraceInputStream
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FatalIssueReporterProcessorTest {
    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage = mock()
    private val fatalIssueReportCaptor = argumentCaptor<ByteArray>()
    private val reportTypeCaptor = argumentCaptor<Byte>()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        fatalIssueReporterProcessor =
            FatalIssueReporterProcessor(APP_CONTEXT, fatalIssueReporterStorage)
    }

    @Test
    fun persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel() {
        val callerThread = Thread("crashing_thread")
        val fakeException = FakeJvmException()

        fatalIssueReporterProcessor.persistJvmCrash(
            FAKE_TIME_STAMP,
            callerThread,
            fakeException,
            null,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(fatalIssueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val error = report.errors(0)!!
        assertThat(error.reason).isEqualTo("Fake JVM exception")
        assertThat(error.name).isEqualTo("io.bitdrift.capture.fakes.FakeJvmException")
        assertThat(error.stackTrace(0)!!.type).isEqualTo(1)
        assertThat(error.stackTrace(0)!!.state(0)).isNull()
        assertThat(
            error.stackTrace(0)!!.className,
        ).isEqualTo("io.bitdrift.capture.FatalIssueReporterProcessorTest")
        assertThat(
            error.stackTrace(0)!!.symbolName,
        ).isEqualTo(
            "persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel",
        )
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueReporterProcessorTest.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(50)
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun persistAppExitReport_whenAnr_shouldCreateNonEmptyErrorModel() {
        val description = APP_EXIT_DESCRIPTION_ANR
        val traceInputStream = createTraceInputStream(APP_EXIT_VALID_ANR_TRACE)

        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(fatalIssueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val error = report.errors(0)!!
        assertThat(error.name).isEqualTo(APP_EXIT_DESCRIPTION_ANR)
        assertThat(error.reason).isEqualTo("User Perceived ANR")
        assertThat(error.stackTrace(0)!!.type).isEqualTo(1)
        assertThat(error.stackTrace(0)!!.stateLength).isEqualTo(0)
        assertThat(error.stackTrace(0)!!.className).isEqualTo("io.bitdrift.capture.FatalIssueGenerator")
        assertThat(error.stackTrace(0)!!.symbolName).isEqualTo("startProcessing")
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueGenerator.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(106)
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun persistAppExitReport_whenUserPerceivedAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Input Dispatching Timed Out",
            expectedReasonMessage = "User Perceived ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBroadcastReceiverAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "Broadcast of Intent { act=android.intent.action.MAIN " +
                    "cmp=com.example.app/.MainActivity}",
            expectedReasonMessage = "Broadcast Receiver ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenExecutingServiceAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "Executing service. { act=android.intent.action.MAIN \" +\n" +
                    "                    \"cmp=com.example.app/.MainActivity}",
            expectedReasonMessage = "Executing Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenStartServiceForegroundAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Service.StartForeground() not called.{ act=android.intent.action.MAIN}",
            expectedReasonMessage = "Service.startForeground() Not Called ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenContentProviderTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "My Application. Content Provider Timeout",
            expectedReasonMessage = "Content Provider ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppRegisteredTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "My Application. App Registered Timeout",
            expectedReasonMessage = "App Registered ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenShortFgsTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Foreground service ANR. Short FGS Timeout. Duration=5000ms",
            expectedReasonMessage = "Short Foreground Service Timeout ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenSystemJobServiceTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "SystemJobService. Job Service Timeout",
            expectedReasonMessage = "Job Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppStartupTimeOut_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "App start timeout. Timeout=5000ms",
            expectedReasonMessage = "App Start ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenServiceStartTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Service start timeout. Timeout=5000ms",
            expectedReasonMessage = "Service Start ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenGenericAnrTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "It's full moon ANR",
            expectedReasonMessage = "Undetermined ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenNativeCrash_shouldCreateEmptyErrorModel() {
        val description = "Native crash"
        val traceInputStream = createTraceInputStream("sample native crash trace")

        fatalIssueReporterProcessor.persistAppExitReport(
            ReportType.NativeCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(fatalIssueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        assertThat(report.errorsLength).isEqualTo(0)
    }

    @Test
    fun persistAppExitReport_withInvalidReason_shouldNotInteractWithStorage() {
        val description = null
        val traceInputStream = createTraceInputStream("sample native crash trace")

        fatalIssueReporterProcessor.persistAppExitReport(
            ReportType.JVMCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage, never())
            .persistFatalIssue(any(), any(), any())
    }

    private fun assertAnrReason(
        descriptionFromAppExit: String,
        expectedReasonMessage: String,
    ) {
        val traceInputStream = createTraceInputStream(APP_EXIT_VALID_ANR_TRACE)

        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            descriptionFromAppExit,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val report = Report.getRootAsReport(ByteBuffer.wrap(fatalIssueReportCaptor.firstValue))
        assertThat(report.errors(0)).isNotNull
        report.errors(0)?.let { error ->
            assertThat(error.reason).isEqualTo(expectedReasonMessage)
        }
    }

    private companion object {
        const val FAKE_TIME_STAMP = 1241515210914L
        const val APP_EXIT_DESCRIPTION_ANR =
            "Input dispatching timed out (219180 " +
                "io.bitdrift.capture/io.bitdrift.capture.MainActivity (server) " +
                "is not responding. Waited 5004ms for MotionEvent"

        // TODO(FranAguilera): BIT-5142 use raw files
        const val APP_EXIT_VALID_ANR_TRACE =
            "\"main\" prio=5 tid=1 Blocked\n" +
                "  | group=\"main\" sCount=1 ucsCount=0 flags=1 obj=0x721b0f98 self=0xb400007d136a27b0\n" +
                "  | sysTid=3994 nice=-10 cgrp=top-app sched=0/0 handle=0x7f053014f8\n" +
                "  | state=S schedstat=( 979645230 62026944 1016 ) utm=91 stm=6 core=0 HZ=100\n" +
                "  | stack=0x7ffccb0000-0x7ffccb2000 stackSize=8188KB\n" +
                "  | held mutexes=\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator.startProcessing(FatalIssueGenerator.kt:106)\n" +
                "  - waiting to lock <0x0481d03d> (a java.lang.String) held by thread 4\n" +
                "  - locked <0x04e67032> (a java.lang.String)\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator.forceDeadlockAnr$(FatalIssueGenerator.kt:35)\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator(unavailable:0)\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator.callOnMainThread\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator\n" +
                "  at io.bitdrift.capture.FatalIssueGenerator\n" +
                "  at android.os.Handler.handleCallback(Handler.java:958)\n" +
                "  at android.os.Handler.dispatchMessage(Handler.java:99)\n" +
                "  at android.os.Looper.loopOnce(Looper.java:205)\n" +
                "  at android.os.Looper.loop(Looper.java:294)\n" +
                "  at android.app.ActivityThread.main(ActivityThread.java:8177)\n" +
                "  at java.lang.reflect.Method.invoke(Native method)\n" +
                "  at com.android.internal.os.RuntimeInit.run(RuntimeInit.java:552)\n" +
                "  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)\n"
    }
}
