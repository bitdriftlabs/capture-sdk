// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.createTraceInputStream
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Architecture
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Platform
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.persistence.IReporterIssueStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
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
    private lateinit var attributes: ClientAttributes
    private val fatalIssueReporterStorage: IReporterIssueStorage = mock()
    private val streamingReportProcessor: IStreamingReportProcessor = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val fatalIssueReportCaptor = argumentCaptor<ByteArray>()
    private val reportTypeCaptor = argumentCaptor<Byte>()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        attributes = ClientAttributes(APP_CONTEXT, lifecycleOwner)
        IssueReporterProcessor.init(fatalIssueReporterStorage, attributes, streamingReportProcessor)
    }

    @Test
    fun persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel() {
        val callerThread = Thread("crashing_thread")
        val fakeException = FakeJvmException()

        IssueReporterProcessor.persistJvmCrash(
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
        ).isEqualTo("io.bitdrift.capture.reports.processor.FatalIssueReporterProcessorTest")
        assertThat(
            error.stackTrace(0)!!.symbolName,
        ).isEqualTo(
            "persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel",
        )
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueReporterProcessorTest.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(fakeException.stackTrace[0].lineNumber.toLong())
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun persistJvmCrash_withChainedException_shouldBuildErrors() {
        val exception =
            RuntimeException(
                "OnErrorNotImplementedException",
                IllegalArgumentException("Artificial exception"),
            )
        IssueReporterProcessor.persistJvmCrash(
            FAKE_TIME_STAMP,
            Thread("crashing-thread"),
            exception,
            null,
        )

        verify(fatalIssueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            fatalIssueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )

        val buffer = ByteBuffer.wrap(fatalIssueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val mainError = report.errors(0)!!
        val rootCause = report.errors(1)!!
        assertThat(mainError.name).isEqualTo("java.lang.RuntimeException")
        assertThat(mainError.reason).isEqualTo("OnErrorNotImplementedException")
        assertThat(rootCause.name).isEqualTo("java.lang.IllegalArgumentException")
        assertThat(rootCause.reason).isEqualTo("Artificial exception")
    }

    @Test
    fun persistAppExitReport_whenAnr() {
        doReturn("/some/path/foo.cap").`when`(fatalIssueReporterStorage).generateFatalIssueFilePath()
        val trace = buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt")
        IssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            enableNativeCrashReporting = true,
            FAKE_TIME_STAMP,
            "Input Dispatching Timed Out",
            trace,
        )

        verify(streamingReportProcessor).persistANR(
            eq(trace),
            eq(FAKE_TIME_STAMP),
            eq("/some/path/foo.cap"),
            eq(attributes),
        )
    }

    @Test
    fun persistAppExitReport_whenNativeCrashAndNdkProcessingNotConfigured_shouldNotCreateNativeReport() {
        val description = "Native crash"
        val traceInputStream = buildTraceInputStringFromFile("app_exit_native_crash.bin")

        IssueReporterProcessor.persistAppExitReport(
            ReportType.NativeCrash,
            enableNativeCrashReporting = false,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verifyNoInteractions(fatalIssueReporterStorage)
    }

    @Test
    fun persistAppExitReport_whenNativeCrashAndNdkProcessingConfigured_shouldCreateNativeReport() {
        val description = "Native crash"
        val traceInputStream = buildTraceInputStringFromFile("app_exit_native_crash.bin")

        IssueReporterProcessor.persistAppExitReport(
            ReportType.NativeCrash,
            enableNativeCrashReporting = true,
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
        assertThat(report.errorsLength).isEqualTo(1)

        val capturedError = report.errors(0)!!
        assertThat(capturedError.reason).isEqualTo("Segmentation violation (invalid memory reference)")
        assertThat(capturedError.name).isEqualTo("SIGSEGV")
        val errorStackTrace = capturedError.stackTrace(0)
        assertThat(errorStackTrace).isNotNull
        assertThat(errorStackTrace?.type).isEqualTo(3) // AndroidNative
        assertThat(errorStackTrace?.className).isNull()
        assertThat(errorStackTrace?.sourceFile).isNull()

        val activeThread = report.threadDetails?.threads(36)
        assertThat(activeThread).isNotNull
        assertThat(activeThread?.active).isEqualTo(true)
        assertThat(activeThread?.name).isEqualTo("Thread-3")
        assertThat(activeThread?.stackTrace(0)?.frameAddress).isEqualTo(512588718688UL)

        val binaryImage = report.binaryImages(0)
        assertThat(binaryImage).isNotNull
        assertThat(binaryImage?.path).isEqualTo("/apex/com.android.runtime/lib64/bionic/libc.so")
        assertThat(binaryImage?.id).isEqualTo("a87908b48b368e6282bcc9f34bcfc28c")

        val deviceMetrics = report.deviceMetrics
        assertThat(deviceMetrics).isNotNull
        assertThat(deviceMetrics?.platform).isEqualTo(Platform.Android)
        assertThat(deviceMetrics?.arch).isEqualTo(Architecture.arm32)
        assertThat(deviceMetrics?.cpuAbis(0)).isEqualTo("armeabi-v7a")
    }

    @Test
    fun persistAppExitReport_withInvalidReason_shouldNotInteractWithStorage() {
        val description = null
        val traceInputStream = createTraceInputStream("sample native crash trace")

        IssueReporterProcessor.persistAppExitReport(
            ReportType.JVMCrash,
            enableNativeCrashReporting = true,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage, never())
            .persistFatalIssue(any(), any(), any())
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
        return file.inputStream()
    }

    private companion object {
        const val FAKE_TIME_STAMP = 1241515210914L
        const val APP_EXIT_DESCRIPTION_ANR =
            "Input dispatching timed out (219180 " +
                "io.bitdrift.capture/io.bitdrift.capture.MainActivity (server) " +
                "is not responding. Waited 5004ms for MotionEvent"
    }
}
