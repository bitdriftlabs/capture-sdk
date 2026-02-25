// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.fakes.FakeIssueReporterProcessor
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.reports.processor.IssueReporterProcessor
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.utils.SdkDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // needs API 30 to use ApplicationExitInfo
class IssueReporterTest {
    private lateinit var activityManager: ActivityManager
    private lateinit var issueReporter: IssueReporter
    private lateinit var reportsDir: File
    private lateinit var configFile: File

    private lateinit var sdkDirectory: String
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val completedReportsProcessor: ICompletedReportsProcessor = mock()

    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()

    private val internalLogger: IInternalLogger = mock()
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val clientAttributes = ClientAttributes(appContext, lifecycleOwner)
    private val fakeIssueReporterProcessor = FakeIssueReporterProcessor()
    private val logMessageCaptor = argumentCaptor<() -> String>()
    private val throwableCaptor = argumentCaptor<Throwable>()

    @Before
    fun setup() {
        val initializer = ContextHolder()
        val appContext: Context = ApplicationProvider.getApplicationContext()
        initializer.create(ApplicationProvider.getApplicationContext())

        activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        reportsDir =
            File(
                APP_CONTEXT.filesDir,
                "bitdrift_capture/reports/",
            ).apply { if (!exists()) mkdirs() }
        configFile = File(reportsDir, "config.csv")
        configFile.writeText("crash_reporting.enabled,true")
        sdkDirectory = SdkDirectory.getPath(APP_CONTEXT)
        issueReporter = buildReporter()
    }

    @After
    fun teardown() {
        reportsDir.delete()
        fakeIssueReporterProcessor.reset()
    }

    @Test
    fun initialize_whenDisabledViaConfig_shouldNotInit() {
        configFile.writeText("crash_reporting.enabled,false")
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Disabled::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun initialize_whenConfigCorrupt_shouldNotInit() {
        configFile.writeText("crash_reporting.enabled")
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Invalid::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun initialize_whenConfigNotPresent_shouldNotInit() {
        configFile.delete()
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        issueReporter.issueReporterState.assert(
            IssueReporterState.RuntimeState.Unset::class.java,
        )
        assertThat(
            issueReporter.getLogStatusFieldsMap()["_fatal_issue_reporting_duration_ms"],
        ).isNotNull
    }

    @Test
    fun initialize_whenEnabled_shouldInitCrashHandlerAndFetchAppExitReason() {
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        verify(captureUncaughtExceptionHandler).install(eq(issueReporter))
        verify(latestAppExitInfoProvider).get(any())
        issueReporter.issueReporterState.assert(
            IssueReporterState.Initialized::class.java,
        )
        verify(completedReportsProcessor).processIssueReports(ReportProcessingSession.PreviousRun)
    }

    @Test
    fun onJvmCrash_withExceptionThrownWhilePersisting_shouldLogInternalError() {
        val issueReporter = buildAndInitReporterWithFakeProcessor(shouldThrowWhenProcessingJvmCrash = true)
        val crashingThread = Thread {}
        val originalCrashError = Exception("Original crash exception")

        issueReporter.onJvmCrash(crashingThread, originalCrashError)

        verify(internalLogger).logInternal(
            type = eq(LogType.INTERNALSDK),
            level = eq(LogLevel.ERROR),
            arrayFields = eq(ArrayFields.EMPTY),
            throwable = throwableCaptor.capture(),
            blocking = eq(true),
            message = logMessageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue.message).contains("Critical issue while processing JVM crash")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Error while processing JVM crash")
    }

    @Test
    fun onJvmCrash_withoutExceptionThrownWhilePersisting_shouldNotLogInternalError() {
        val issueReporter = buildAndInitReporterWithFakeProcessor(shouldThrowWhenProcessingJvmCrash = false)
        val crashingThread = Thread {}
        val originalCrashError = Exception("Original crash exception")

        issueReporter.onJvmCrash(crashingThread, originalCrashError)

        verify(internalLogger, never()).logInternal(
            type = any(),
            level = any(),
            arrayFields = any(),
            throwable = any(),
            blocking = any(),
            message = any(),
        )
    }

    private fun IssueReporterState.assert(
        expectedType: Class<*>,
        crashFileExist: Boolean = false,
    ) {
        assertThat(this).isInstanceOf(expectedType)
        assertCrashFile(crashFileExist)
    }

    @Test
    fun init_whenAppExitInfoFails_shouldCallOnErrorOccurred() {
        val exception = RuntimeException("test error")
        whenever(latestAppExitInfoProvider.get(any()))
            .thenThrow(exception)

        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        verify(completedReportsProcessor).onReportProcessingError(
            any(),
            eq(exception),
        )
    }

    @Test
    fun getIssueReporterProcessor_whenNotInitialized_shouldReturnNull() {
        val processor = issueReporter.getIssueReporterProcessor()

        assertThat(processor).isNull()
    }

    @Test
    fun getIssueReporterProcessor_whenInitialized_shouldReturnValidProcessor() {
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        val processor = issueReporter.getIssueReporterProcessor()

        assertThat(processor).isInstanceOf(IssueReporterProcessor::class.java)
    }

    @Test
    fun getIssueReporterProcessor_whenInitCalledButDisabled_shouldReturnNull() {
        configFile.writeText("crash_reporting.enabled,false")
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )

        val processor = issueReporter.getIssueReporterProcessor()

        assertThat(processor).isNull()
    }

    private fun assertCrashFile(crashFileExist: Boolean) {
        val crashFile = File(reportsDir, "/new/latest_crash_info.json")
        assertThat(crashFile.exists()).isEqualTo(crashFileExist)
    }

    private fun buildReporter(): IssueReporter =
        IssueReporter(
            internalLogger,
            FakeBackgroundThreadHandler(),
            latestAppExitInfoProvider,
            captureUncaughtExceptionHandler,
            dateProvider = FakeDateProvider,
        )

    private fun buildAndInitReporterWithFakeProcessor(shouldThrowWhenProcessingJvmCrash: Boolean): IssueReporter {
        fakeIssueReporterProcessor.shouldFailPersistingJvmCrash(shouldThrowWhenProcessingJvmCrash)
        val issueReporter =
            IssueReporter(
                internalLogger,
                FakeBackgroundThreadHandler(),
                latestAppExitInfoProvider,
                captureUncaughtExceptionHandler,
                dateProvider = FakeDateProvider,
                issueReporterProcessorFactory = { _, _, _ -> fakeIssueReporterProcessor },
            )
        issueReporter.init(
            activityManager,
            sdkDirectory,
            clientAttributes,
            completedReportsProcessor,
        )
        return issueReporter
    }
}
