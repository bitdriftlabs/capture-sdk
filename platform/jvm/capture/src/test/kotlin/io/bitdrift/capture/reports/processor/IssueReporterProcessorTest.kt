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
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.fakes.FakeDateProvider.DEFAULT_TEST_TIMESTAMP
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.createTraceInputStream
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Architecture
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Platform
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.persistence.IIssueReporterStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IssueReporterProcessorTest {
    private lateinit var attributes: ClientAttributes
    private val issueReporterStorage: IIssueReporterStore = mock()
    private val streamingReportProcessor: IStreamingReportProcessor = mock()
    private val internalLogger: IInternalLogger = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val issueReportCaptor = argumentCaptor<ByteArray>()
    private val reportTypeCaptor = argumentCaptor<Byte>()
    private val throwableCaptor = argumentCaptor<Throwable>()
    private val logMessageCaptor = argumentCaptor<() -> String>()

    private lateinit var processor: IssueReporterProcessor

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        attributes = ClientAttributes(APP_CONTEXT, lifecycleOwner)
        processor =
            IssueReporterProcessor(
                issueReporterStorage,
                attributes,
                streamingReportProcessor,
                FakeDateProvider,
                internalLogger,
            )
    }

    @Test
    fun processJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel() {
        val callerThread = Thread("crashing_thread")
        val fakeException = FakeJvmException()

        processor.processJvmCrash(
            callerThread,
            fakeException,
            null,
        )

        verify(issueReporterStorage).persistFatalIssue(
            eq(DEFAULT_TEST_TIMESTAMP),
            issueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(issueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val error = report.errors(0)!!
        assertThat(error.reason).isEqualTo("Fake JVM exception")
        assertThat(error.name).isEqualTo("io.bitdrift.capture.fakes.FakeJvmException")
        assertThat(error.stackTrace(0)!!.type).isEqualTo(1)
        assertThat(error.stackTrace(0)!!.state(0)).isNull()
        assertThat(
            error.stackTrace(0)!!.className,
        ).isEqualTo("io.bitdrift.capture.reports.processor.IssueReporterProcessorTest")
        assertThat(
            error.stackTrace(0)!!.symbolName,
        ).isEqualTo(
            "processJvmCrash_withFakeException_shouldCreateNonEmptyErrorModel",
        )
        assertThat(error.stackTrace(0)!!.sourceFile!!.path).isEqualTo("IssueReporterProcessorTest.kt")
        assertThat(error.stackTrace(0)!!.sourceFile!!.line).isEqualTo(fakeException.stackTrace[0].lineNumber.toLong())
        assertThat(error.stackTrace(0)!!.sourceFile!!.column).isEqualTo(0)
    }

    @Test
    fun processJvmCrash_withChainedException_shouldBuildErrors() {
        val exception =
            RuntimeException(
                "OnErrorNotImplementedException",
                IllegalArgumentException("Artificial exception"),
            )
        processor.processJvmCrash(
            Thread("crashing-thread"),
            exception,
            null,
        )

        verify(issueReporterStorage).persistFatalIssue(
            eq(DEFAULT_TEST_TIMESTAMP),
            issueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )

        val buffer = ByteBuffer.wrap(issueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val mainError = report.errors(0)!!
        val rootCause = report.errors(1)!!
        assertThat(mainError.name).isEqualTo("java.lang.RuntimeException")
        assertThat(mainError.reason).isEqualTo("OnErrorNotImplementedException")
        assertThat(rootCause.name).isEqualTo("java.lang.IllegalArgumentException")
        assertThat(rootCause.reason).isEqualTo("Artificial exception")
    }

    @Test
    fun processJvmCrash_whenPersistFails_shouldLogInternalError() {
        doThrow(RuntimeException("persist failed"))
            .`when`(issueReporterStorage)
            .persistFatalIssue(any(), any(), any())

        processor.processJvmCrash(
            Thread("crashing-thread"),
            RuntimeException("crash"),
            null,
        )

        verify(internalLogger).logInternalError(
            throwable = throwableCaptor.capture(),
            blocking = eq(true),
            message = logMessageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue).isInstanceOf(RuntimeException::class.java)
        assertThat(throwableCaptor.firstValue.message).isEqualTo("persist failed")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Error while processing and persisting a JVM report")
    }

    @Test
    fun processAppExitReport_whenAnr() {
        doReturn("/some/path/foo.cap").`when`(issueReporterStorage).generateFatalIssueFilePath()
        val trace = buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt")
        processor.processAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            "Input Dispatching Timed Out",
            trace,
            signalNumber = 0,
        )

        verify(streamingReportProcessor).processAndPersistANR(
            eq(trace),
            eq(FAKE_TIME_STAMP),
            eq("/some/path/foo.cap"),
            eq(attributes),
        )
    }

    @Test
    fun processAppExitReport_whenAnrPersistThrowsRuntimeWithIOExceptionCause_shouldLogInternalErrorWithExpectedException() {
        val exception =
            RuntimeException(
                "jni persist ANR: wrapped",
                IOException("jni persist ANR: Permission denied (os error 13)"),
            )
        setReportDirectoryAndThrowException(exception)

        processor.processAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            "Input Dispatching Timed Out",
            buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt"),
            signalNumber = 0,
        )

        verify(internalLogger).logInternalError(
            throwable = throwableCaptor.capture(),
            blocking = eq(false),
            message = logMessageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue).isInstanceOf(RuntimeException::class.java)
        assertThat(throwableCaptor.firstValue.cause).isInstanceOf(IOException::class.java)
        assertThat(throwableCaptor.firstValue.cause?.message).contains("jni persist ANR")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Error while processing and persisting an AppExit report")
    }

    @Test
    fun processAppExitReport_whenAnrPersistThrowsIllegalArgumentException_shouldLogInternalErrorWithExpectedException() {
        val exception = IllegalArgumentException("jni persist ANR: failed to parse destination: bad value")
        setReportDirectoryAndThrowException(exception)

        processor.processAppExitReport(
            fatalIssueType = ReportType.AppNotResponding,
            FAKE_TIME_STAMP,
            "Input Dispatching Timed Out",
            buildTraceInputStringFromFile("app_exit_anr_deadlock_anr.txt"),
            signalNumber = 0,
        )

        verify(internalLogger).logInternalError(
            throwable = throwableCaptor.capture(),
            blocking = eq(false),
            message = logMessageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(throwableCaptor.firstValue.message).contains("jni persist ANR")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Error while processing and persisting an AppExit report")
    }

    @Test
    fun processAppExitReport_whenNativeCrash_shouldCreateNativeReport() {
        val description = "Native crash"
        val traceInputStream = buildTraceInputStringFromFile("app_exit_native_crash.bin")

        processor.processAppExitReport(
            ReportType.NativeCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
            signalNumber = 0,
        )

        verify(issueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            issueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(issueReportCaptor.firstValue)
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
    fun processAppExitReport_whenNativeCrashWithNullTrace_shouldCreateSkeletonNativeReport() {
        val description = "Segmentation fault"

        processor.processAppExitReport(
            ReportType.NativeCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream = null,
            signalNumber = 11, // SIGSEGV
        )

        verify(issueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            issueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(issueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        assertThat(report.errorsLength).isEqualTo(1)
        assertThat(reportTypeCaptor.firstValue).isEqualTo(ReportType.NativeCrash)

        val capturedError = report.errors(0)!!
        assertThat(capturedError.name).isEqualTo("SIGSEGV")
        assertThat(capturedError.reason).isEqualTo("Segmentation violation (invalid memory reference)")
        assertThat(capturedError.stackTraceLength).isEqualTo(0)
        assertThat(report.threadDetails?.threadsLength).isEqualTo(0)
        assertThat(report.binaryImagesLength).isEqualTo(0)
    }

    @Test
    fun processAppExitReport_whenNativeCrashWithNullTraceAndUnknownSignal_shouldFallBackToDescription() {
        val description = "Segmentation fault"

        processor.processAppExitReport(
            ReportType.NativeCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream = null,
            signalNumber = 0,
        )

        verify(issueReporterStorage).persistFatalIssue(
            eq(FAKE_TIME_STAMP),
            issueReportCaptor.capture(),
            reportTypeCaptor.capture(),
        )
        val buffer = ByteBuffer.wrap(issueReportCaptor.firstValue)
        val report = Report.getRootAsReport(buffer)
        val capturedError = report.errors(0)!!
        assertThat(capturedError.name).isEqualTo(description)
        assertThat(capturedError.reason).isEqualTo("Native crash")
    }

    @Test
    fun processAppExitReport_withInvalidReason_shouldNotInteractWithStorage() {
        val description = null
        val traceInputStream = createTraceInputStream("sample native crash trace")

        processor.processAppExitReport(
            ReportType.JVMCrash,
            FAKE_TIME_STAMP,
            description,
            traceInputStream,
            signalNumber = 0,
        )

        verify(issueReporterStorage, never())
            .persistFatalIssue(any(), any(), any())
    }

    @Test
    fun processJavaScriptReport_withFatalIssue_shouldCallStreamingProcessorWithCorrectArguments() {
        doReturn(FAKE_FATAL_PATH).`when`(issueReporterStorage).generateFatalIssueFilePath()

        persistJavaScriptError(isFatalIssue = true)

        assertJavaScriptArguments(expectedFatalIssue = true)
    }

    @Test
    fun processJavaScriptReport_withNonFatalIssue_shouldCallStreamingProcessorWithCorrectArguments() {
        doReturn(FAKE_NON_FATAL_PATH).`when`(issueReporterStorage).generateNonFatalIssueFilePath()

        persistJavaScriptError(isFatalIssue = false)

        assertJavaScriptArguments(expectedFatalIssue = false)
    }

    @Test
    fun processJavaScriptReport_whenStreamingProcessorFails_shouldLogInternalError() {
        doReturn(FAKE_FATAL_PATH).`when`(issueReporterStorage).generateFatalIssueFilePath()
        doThrow(RuntimeException("js persist failed"))
            .`when`(streamingReportProcessor)
            .processAndPersistJavaScriptError(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())

        persistJavaScriptError(isFatalIssue = true)

        verify(internalLogger).logInternalError(
            throwable = throwableCaptor.capture(),
            blocking = eq(false),
            message = logMessageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue).isInstanceOf(RuntimeException::class.java)
        assertThat(throwableCaptor.firstValue.message).isEqualTo("js persist failed")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Error while persisting and processing a JavaScriptError")
    }

    private fun assertJavaScriptArguments(expectedFatalIssue: Boolean) {
        val expectedPath = if (expectedFatalIssue) FAKE_FATAL_PATH else FAKE_NON_FATAL_PATH
        verify(streamingReportProcessor).processAndPersistJavaScriptError(
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
        processor.processJavaScriptReport(
            errorName = FAKE_ERROR_NAME,
            message = FAKE_ERROR_MESSAGE,
            stack = FAKE_STACK_TRACE,
            isFatalIssue = isFatalIssue,
            engine = FAKE_ENGINE_JSC,
            debugId = FAKE_DEBUG_ID,
            sdkVersion = RN_BITDRIFT_VERSION,
        )
    }

    private fun buildTraceInputStringFromFile(rawFilePath: String): InputStream =
        io.bitdrift.capture.TestResourceHelper
            .getResourceAsStream(rawFilePath)

    private fun setReportDirectoryAndThrowException(exception: Exception) {
        doReturn("/some/path/foo.cap").`when`(issueReporterStorage).generateFatalIssueFilePath()
        doThrow(exception)
            .`when`(streamingReportProcessor)
            .processAndPersistANR(any(), eq(FAKE_TIME_STAMP), eq("/some/path/foo.cap"), eq(attributes))
    }

    private companion object {
        const val FAKE_TIME_STAMP = 1241515210914L
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
