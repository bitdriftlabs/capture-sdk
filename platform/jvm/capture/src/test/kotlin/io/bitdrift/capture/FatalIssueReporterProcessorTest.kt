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
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Paths

/**
 * WARNING: For now these test only run on bazel given the difference between accessing
 * test resources on gradle vs Bazel
 */
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
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(56)
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun persistAppExitReport_whenAnr_shouldCreateNonEmptyErrorModel() {
        val description = APP_EXIT_DESCRIPTION_ANR
        val traceInputStream = buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt")

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
        assertThat(error.name).isEqualTo("User Perceived ANR")
        assertThat(error.reason).isEqualTo(APP_EXIT_DESCRIPTION_ANR)
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
        assertAnrContents(
            descriptionFromAppExit = "Input Dispatching Timed Out",
            expectedMessage = "User Perceived ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBroadcastReceiverAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit =
                "Broadcast of Intent { act=android.intent.action.MAIN " +
                    "cmp=com.example.app/.MainActivity}",
            expectedMessage = "Broadcast Receiver ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenExecutingServiceAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit =
                "Executing service. { act=android.intent.action.MAIN \" +\n" +
                    "                    \"cmp=com.example.app/.MainActivity}",
            expectedMessage = "Executing Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenStartServiceForegroundAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "Service.StartForeground() not called.{ act=android.intent.action.MAIN}",
            expectedMessage = "Service.startForeground() Not Called ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenContentProviderTimeoutAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "My Application. Content Provider Timeout",
            expectedMessage = "Content Provider ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppRegisteredTimeoutAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "My Application. App Registered Timeout",
            expectedMessage = "App Registered ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenShortFgsTimeoutAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "Foreground service ANR. Short FGS Timeout. Duration=5000ms",
            expectedMessage = "Short Foreground Service Timeout ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenSystemJobServiceTimeoutAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "SystemJobService. Job Service Timeout",
            expectedMessage = "Job Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppStartupTimeOut_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "App start timeout. Timeout=5000ms",
            expectedMessage = "App Start ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenServiceStartTimeout_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "Service start timeout. Timeout=5000ms",
            expectedMessage = "Service Start ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBackgroundAnr_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit = "It's full moon ANR",
            expectedMessage = "Undetermined ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenGenericAnrTimeout_shouldMatchAnrReason() {
        assertAnrContents(
            descriptionFromAppExit =
                "bg anr: Process " +
                    "ProcessRecord{9707291 4609:io.bitdrift.gradletestapp/u0a207} " +
                    "failed to complete startup\n",
            expectedMessage = "Background ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenNativeCrash_shouldCreateEmptyErrorModel() {
        val description = "Native crash"
        val traceInputStream = buildTraceInputStringFromFile("app_exit_native_crash.txt")

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

    private fun assertAnrContents(
        descriptionFromAppExit: String,
        expectedMessage: String,
    ) {
        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            descriptionFromAppExit,
            buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt"),
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val report = Report.getRootAsReport(ByteBuffer.wrap(fatalIssueReportCaptor.firstValue))
        assertThat(report.errors(0)).isNotNull
        report.errors(0)?.let { error ->
            assertThat(error.name).isEqualTo(expectedMessage)
            val frame = error.stackTrace(0)
            assertThat(frame).isNotNull
            assertThat(frame!!.sourceFile!!.line).isEqualTo(106)
            assertThat(frame.sourceFile!!.path).isEqualTo("FatalIssueGenerator.kt")
            assertThat(frame.symbolName).isEqualTo("startProcessing")
            assertThat(frame.className).isEqualTo("io.bitdrift.capture.FatalIssueGenerator")
        }
    }

    private fun buildTraceInputStringFromFile(rawFilePath: String): InputStream {
        val file =
            Paths
                .get(
                    System.getenv("TEST_SRCDIR"),
                    "_main",
                    "platform/jvm/capture/src/test/resources",
                    rawFilePath,
                ).toFile()
        val resourceStream = file.inputStream()
        val anrRawTrace = resourceStream.bufferedReader().use { it.readText() }
        return createTraceInputStream(anrRawTrace)
    }

    private companion object {
        const val FAKE_TIME_STAMP = 1241515210914L
        const val APP_EXIT_DESCRIPTION_ANR =
            "Input dispatching timed out (219180 " +
                "io.bitdrift.capture/io.bitdrift.capture.MainActivity (server) " +
                "is not responding. Waited 5004ms for MotionEvent"
    }
}
