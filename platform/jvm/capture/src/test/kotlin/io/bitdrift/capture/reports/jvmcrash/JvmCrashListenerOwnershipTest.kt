// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.jvmcrash

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.AppExitLogger
import io.bitdrift.capture.fakes.DeferredBackgroundThreadHandler
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.reports.IssueReporter
import io.bitdrift.capture.reports.IssueReporterState
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor
import io.bitdrift.capture.utils.BuildVersionChecker
import io.bitdrift.capture.utils.SdkDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers which of the two JVM crash reporters is allowed to report an uncaught JVM exception.
 *
 * Ownership is decided once, at wiring time: [IssueReporter] owns uncaught JVM exceptions when its
 * constructor initialized fatal issue reporting, and the legacy [AppExitLogger] otherwise. These
 * tests reproduce the production wiring in `LoggerImpl` with real collaborators, so exactly one of
 * the two must report any given crash regardless of what has or hasn't happened since wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // needs API 30 to use ApplicationExitInfo
class JvmCrashListenerOwnershipTest {
    private val internalLogger: IInternalLogger = mock()
    private val runtime: Runtime = mock()
    private val versionChecker: BuildVersionChecker = mock()
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()
    private val completedReportsProcessor: ICompletedReportsProcessor = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()

    private val backgroundThreadHandler = DeferredBackgroundThreadHandler()
    private val captureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler()

    private lateinit var reportsDir: File
    private lateinit var sdkDirectory: String
    private lateinit var clientAttributes: ClientAttributes
    private lateinit var issueReporter: IssueReporter
    private lateinit var appExitLogger: AppExitLogger

    private var previousDefaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        ContextHolder().create(ApplicationProvider.getApplicationContext())
        clientAttributes = ClientAttributes(ApplicationProvider.getApplicationContext<Context>(), lifecycleOwner)

        reportsDir =
            File(
                APP_CONTEXT.filesDir,
                "bitdrift_capture/reports/",
            ).apply { if (!exists()) mkdirs() }
        File(reportsDir, "config.csv").writeText("crash_reporting.enabled,true")
        sdkDirectory = SdkDirectory.getPath(APP_CONTEXT)

        whenever(runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)).thenReturn(true)
        whenever(versionChecker.isAtLeast(anyInt())).thenReturn(true)
        // keeps installAppExitLogger from emitting a previous-run AppExit log, so the assertions
        // below only ever see logs produced by the simulated crash
        whenever(latestAppExitInfoProvider.get()).thenReturn(LatestAppExitReasonResult.None)

        previousDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousDefaultExceptionHandler)
        reportsDir.deleteRecursively()
    }

    @Test
    fun uncaughtException_beforePriorReportsProcessingIsRequested_shouldOnlyBeReportedAsFatalIssue() {
        wireCrashReporting()

        // The crash lands after LoggerImpl wiring but before Capture.start() asks for prior
        // reports to be processed. Ownership was already fixed at construction, so the fatal
        // issue reporting path owns this crash.
        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
    }

    @Test
    fun uncaughtException_whilePriorReportsProcessingIsPending_shouldOnlyBeReportedAsFatalIssue() {
        wireCrashReporting()
        issueReporter.processPriorReports(completedReportsProcessor)

        assertThat(issueReporter.issueReporterState).isEqualTo(IssueReporterState.Initializing)
        assertThat(backgroundThreadHandler.hasPendingTasks).isTrue()

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
    }

    @Test
    fun uncaughtException_afterPriorReportsAreProcessed_shouldOnlyBeReportedAsFatalIssue() {
        wireCrashReporting()
        issueReporter.processPriorReports(completedReportsProcessor)
        backgroundThreadHandler.runPendingOnCurrentThread()

        assertThat(issueReporter.issueReporterState).isEqualTo(IssueReporterState.Initialized)

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
    }

    @Test
    fun uncaughtException_afterPriorReportsProcessingFailed_shouldOnlyBeReportedAsFatalIssue() {
        doThrow(RuntimeException("prior report processing failed"))
            .whenever(completedReportsProcessor)
            .processIssueReports(any())

        wireCrashReporting()
        issueReporter.processPriorReports(completedReportsProcessor)
        backgroundThreadHandler.runPendingOnCurrentThread()

        // Processing prior reports is the only thing that failed. Ownership was fixed at
        // construction and the processor is still live, so fatal issue reporting keeps handling
        // JVM crashes despite the state saying otherwise.
        assertThat(issueReporter.issueReporterState).isEqualTo(IssueReporterState.InitializationFailed)
        assertThat(issueReporter.handlesJvmCrashes).isTrue()

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
    }

    @Test
    fun uncaughtException_whenFatalIssueReportingIsDisabledByConfig_shouldBeReportedByAppExitLogger() {
        File(reportsDir, "config.csv").writeText("crash_reporting.enabled,false")

        wireCrashReporting()
        issueReporter.processPriorReports(completedReportsProcessor)

        // The reporter never took ownership, so AppExitLogger is the only crash reporter listening.
        assertThat(issueReporter.handlesJvmCrashes).isFalse
        assertThat(issueReporter.issueReporterState).isEqualTo(IssueReporterState.RuntimeState.Disabled)

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        // suppressing here would drop the crash entirely, which is worse than reporting it twice
        verify(internalLogger).logInternal(
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
        )
        verify(internalLogger, never()).notifyMemoryPressureLevel(any())
    }

    /**
     * Reproduces the crash reporting wiring in `LoggerImpl`: construct the reporter (which decides
     * ownership), register exactly one of the two crash reporters, then install the app exit
     * logger.
     */
    private fun wireCrashReporting() {
        issueReporter =
            IssueReporter(
                internalLogger = internalLogger,
                backgroundThreadHandler = backgroundThreadHandler,
                latestAppExitInfoProvider = latestAppExitInfoProvider,
                dateProvider = FakeDateProvider,
                memoryMetricsProvider = memoryMetricsProvider,
                sdkDirectory = sdkDirectory,
                clientAttributes = clientAttributes,
                loggerId = TEST_LOGGER_ID,
            )

        if (issueReporter.handlesJvmCrashes) {
            captureUncaughtExceptionHandler.install(issueReporter)
        }

        appExitLogger =
            AppExitLogger(
                logger = internalLogger,
                runtime = runtime,
                versionChecker = versionChecker,
                memoryMetricsProvider = memoryMetricsProvider,
                latestAppExitInfoProvider = latestAppExitInfoProvider,
                captureUncaughtExceptionHandler = captureUncaughtExceptionHandler,
                fatalIssueReportingHandlesJvmCrashes = issueReporter.handlesJvmCrashes,
            )
        appExitLogger.installAppExitLogger()
    }

    /**
     * Fatal issue reporting handled the crash, and the legacy [AppExitLogger] path did not also
     * emit its blocking AppExit log for the same crash.
     */
    private fun assertOnlyReportedAsFatalIssue() {
        verify(internalLogger).notifyMemoryPressureLevel(any())
        verify(internalLogger, never()).logInternal(
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
        )
    }

    private companion object {
        const val TEST_LOGGER_ID = 1L
        val SIMULATED_CRASH = IllegalStateException("Simulated Crash")
    }
}
