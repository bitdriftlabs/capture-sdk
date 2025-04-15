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
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FatalIssueReporterProcessorTest {
    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage = mock()
    private val fatalIssueReportCaptor = argumentCaptor<FatalIssueReport>()

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
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            eq(FatalIssueType.JVM_CRASH),
            fatalIssueReportCaptor.capture(),
        )
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].reason).isEqualTo("Fake JVM exception")
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].name).isEqualTo("io.bitdrift.capture.fakes.FakeJvmException")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .type,
        ).isEqualTo(1)
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .state,
        ).isEmpty()
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .className,
        ).isEqualTo("io.bitdrift.capture.FatalIssueReporterProcessorTest")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .symbolName,
        ).isEqualTo(
            "io.bitdrift.capture.FatalIssueReporterProcessorTest" +
                ".persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel",
        )
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.path,
        ).isEqualTo("FatalIssueReporterProcessorTest.kt")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.lineNumber,
        ).isEqualTo(48)
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.column,
        ).isEqualTo(0)
    }

    @Test
    fun persistAppExitReport_whenAnr_shouldCreateNonEmptyErrorModel() {
        val description = APP_EXIT_DESCRIPTION_ANR
        val traceInputStream = createTraceInputStream(APP_EXIT_VALID_ANR_TRACE)

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
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].reason)
            .isEqualTo(APP_EXIT_DESCRIPTION_ANR)
        assertThat(fatalIssueReportCaptor.firstValue.errors[0].name)
            .isEqualTo("at io.bitdrift.capture.FatalIssueGenerator.startProcessing(FatalIssueGenerator.kt:106)")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .type,
        ).isEqualTo(1)
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .state,
        ).isEmpty()
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .className,
        ).isEqualTo("io.bitdrift.capture.FatalIssueGenerator")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .symbolName,
        ).isEqualTo("io.bitdrift.capture.FatalIssueGenerator.startProcessing")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.path,
        ).isEqualTo("FatalIssueGenerator.kt")
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.lineNumber,
        ).isEqualTo(106)
        assertThat(
            fatalIssueReportCaptor.firstValue.errors[0]
                .stackTrace[0]
                .sourceFile.column,
        ).isEqualTo(0)
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
