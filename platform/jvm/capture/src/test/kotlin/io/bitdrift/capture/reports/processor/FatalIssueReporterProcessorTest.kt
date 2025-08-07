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
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.createTraceInputStream
import io.bitdrift.capture.reports.binformat.v1.Architecture
import io.bitdrift.capture.reports.binformat.v1.Platform
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
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
    private lateinit var fatalIssueReporterProcessor: FatalIssueReporterProcessor
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val fatalIssueReportCaptor = argumentCaptor<ByteArray>()
    private val reportTypeCaptor = argumentCaptor<Byte>()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        fatalIssueReporterProcessor =
            FatalIssueReporterProcessor(fatalIssueReporterStorage, ClientAttributes(APP_CONTEXT, lifecycleOwner))
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
        ).isEqualTo("io.bitdrift.capture.reports.processor.FatalIssueReporterProcessorTest")
        assertThat(
            error.stackTrace(0)!!.symbolName,
        ).isEqualTo(
            "persistJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel",
        )
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueReporterProcessorTest.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(62)
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun persistJvmCrash_withChainedException_shouldBuildErrors() {
        val exception =
            RuntimeException(
                "OnErrorNotImplementedException",
                IllegalArgumentException("Artificial exception"),
            )
        fatalIssueReporterProcessor.persistJvmCrash(
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
    fun persistAppExitReport_whenAnr_shouldCreateNonEmptyErrorModel() {
        val description = APP_EXIT_DESCRIPTION_ANR
        val traceInputStream = buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt")

        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
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
        val error = report.errors(0)!!
        assertThat(error.name).isEqualTo("User Perceived ANR")
        assertThat(error.reason).isEqualTo(APP_EXIT_DESCRIPTION_ANR)

        // Entries below corresponds to sample `app_exit_anr_deadlock_anr.txt`
        assertThat(error.stackTrace(0)!!.type).isEqualTo(1)
        assertThat(error.stackTrace(0)!!.stateLength).isEqualTo(0)
        assertThat(error.stackTrace(0)!!.className).isEqualTo("io.bitdrift.capture.FatalIssueGenerator")
        assertThat(error.stackTrace(0)!!.symbolName).isEqualTo("startProcessing")
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueGenerator.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(106)
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)

        val blockedThread = report.threadDetails!!.threads(0)!!
        assertThat(blockedThread.name).isEqualTo("background_thread_for_deadlock_demo")
        assertThat(blockedThread.active).isEqualTo(false)
        assertThat(blockedThread.state).isEqualTo("Blocked")
        assertThat(blockedThread.index).isEqualTo(4U)
        assertThat(blockedThread.priority).isEqualTo(5F)
        assertThat(blockedThread.stackTrace(0)!!.className).contains("io.bitdrift.capture.FatalIssueGenerator")
        assertThat(blockedThread.stackTrace(0)!!.symbolName).isEqualTo("run")
        assertThat(blockedThread.stackTrace(0)!!.sourceFile!!.path).isEqualTo("FatalIssueGenerator.kt")
        assertThat(blockedThread.stackTrace(0)!!.sourceFile!!.line).isEqualTo(91)
        assertThat(blockedThread.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)

        val activeThread = report.threadDetails!!.threads(5)!!
        assertThat(activeThread.name).isEqualTo("Signal Catcher")
        assertThat(activeThread.active).isEqualTo(true)
        assertThat(activeThread.state).isEqualTo("Runnable")
        assertThat(activeThread.index).isEqualTo(2U)
        assertThat(activeThread.priority).isEqualTo(10F)
        assertThat(activeThread.stackTrace(0)!!.className).contains("/apex/com.android.art/lib64/libart.so")
    }

    @Test
    fun persistAppExitReport_whenUserPerceivedAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Input Dispatching Timed Out",
            expectedMessage = "User Perceived ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAnrDialogShown_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "user request after error",
            expectedMessage = "User Perceived ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBroadcastReceiverAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "Broadcast of Intent { act=android.intent.action.MAIN " +
                    "cmp=com.example.app/.MainActivity}",
            expectedMessage = "Broadcast Receiver ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenExecutingServiceAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "Executing service. { act=android.intent.action.MAIN \" +\n" +
                    "                    \"cmp=com.example.app/.MainActivity}",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenStartServiceForegroundAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Service.StartForeground() not called.{ act=android.intent.action.MAIN}",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenServiceBindTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "user request after error: Timed out while trying to bind",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenContentProviderTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "My Application. Content Provider Timeout",
            expectedMessage = "Content Provider ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppRegisteredTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "My Application. App Registered Timeout",
            expectedMessage = "App Registered ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenAppStartupTimeOut_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "App start timeout. Timeout=5000ms",
            expectedMessage = "App Start ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenServiceStartTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Service start timeout. Timeout=5000ms",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenShortFgsTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "Foreground service ANR. Short FGS Timeout. Duration=5000ms",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenSystemJobServiceTimeoutAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "SystemJobService. Job Service Timeout",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenJobServiceStartTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "job service timeout",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenJobServiceStopTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "user request after error: No response to onStopJob",
            expectedMessage = "Service ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBackgroundAnr_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit = "It's full moon ANR",
            expectedMessage = "Undetermined ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenGenericAnrTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "bg anr: Process " +
                    "ProcessRecord{9707291 4609:io.bitdrift.gradletestapp/u0a207} " +
                    "failed to complete startup\n",
            expectedMessage = "Background ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenBgAnrAndInputDispatchingTimeout_shouldMatchAnrReason() {
        assertAnrReason(
            descriptionFromAppExit =
                "bg anr: Input dispatching timed out (85a07c0 " +
                    "com.acme.app/com.acme.app.MainActivity is not responding. " +
                    "Waited 5001ms for MotionEvent)\n",
            expectedMessage = "Background ANR",
        )
    }

    @Test
    fun persistAppExitReport_whenNativeCrashAndNdkProcessingNotConfigured_shouldNotCreateNativeReport() {
        val description = "Native crash"
        val traceInputStream = buildTraceInputStringFromFile("app_exit_native_crash.bin")

        fatalIssueReporterProcessor.persistAppExitReport(
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

        fatalIssueReporterProcessor.persistAppExitReport(
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
        assertThat(capturedError.reason).isEqualTo("Bus error (bad memory access)")
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

        fatalIssueReporterProcessor.persistAppExitReport(
            ReportType.JVMCrash,
            enableNativeCrashReporting = true,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
        )

        verify(fatalIssueReporterStorage, never())
            .persistFatalIssue(any(), any(), any())
    }

    private fun assertAnrReason(
        descriptionFromAppExit: String,
        expectedMessage: String,
    ) {
        fatalIssueReporterProcessor.persistAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            enableNativeCrashReporting = true,
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
