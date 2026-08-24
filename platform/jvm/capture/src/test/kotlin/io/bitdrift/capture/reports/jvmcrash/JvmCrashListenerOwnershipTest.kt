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
 * Covers which of the two registered [IJvmCrashListener]s is allowed to report an uncaught JVM
 * exception.
 *
 * [AppExitLogger] and [IssueReporter] are both installed into the same
 * [CaptureUncaughtExceptionHandler], so exactly one of them must report any given crash. These
 * tests deliberately wire the real collaborators together rather than stubbing the reporter state,
 * so they stay valid regardless of how the hand-off between the two listeners is implemented.
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

        issueReporter =
            IssueReporter(
                internalLogger = internalLogger,
                backgroundThreadHandler = backgroundThreadHandler,
                latestAppExitInfoProvider = latestAppExitInfoProvider,
                captureUncaughtExceptionHandler = captureUncaughtExceptionHandler,
                dateProvider = FakeDateProvider,
                memoryMetricsProvider = memoryMetricsProvider,
            )
        appExitLogger =
            AppExitLogger(
                logger = internalLogger,
                runtime = runtime,
                versionChecker = versionChecker,
                memoryMetricsProvider = memoryMetricsProvider,
                latestAppExitInfoProvider = latestAppExitInfoProvider,
                captureUncaughtExceptionHandler = captureUncaughtExceptionHandler,
                issueReporter = issueReporter,
            )

        previousDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousDefaultExceptionHandler)
        reportsDir.deleteRecursively()
    }

    @Test
    fun uncaughtException_whileIssueReporterIsInitializing_shouldOnlyBeReportedAsFatalIssue() {
        appExitLogger.installAppExitLogger()
        issueReporter.init(sdkDirectory, clientAttributes, completedReportsProcessor, TEST_LOGGER_ID)

        // init() registers the fatal issue reporting crash listener before handing prior-report
        // processing to the background thread, so the reporter owns crashes from this point on
        // even though it has not reached Initialized yet.
        assertThat(issueReporter.initializationState()).isEqualTo(IssueReporterState.Initializing)
        assertThat(backgroundThreadHandler.hasPendingTasks).isTrue()

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
    }

    @Test
    fun uncaughtException_afterIssueReporterIsInitialized_shouldOnlyBeReportedAsFatalIssue() {
        appExitLogger.installAppExitLogger()
        issueReporter.init(sdkDirectory, clientAttributes, completedReportsProcessor, TEST_LOGGER_ID)
        backgroundThreadHandler.runPendingOnCurrentThread()

        assertThat(issueReporter.initializationState()).isEqualTo(IssueReporterState.Initialized)

        captureUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), SIMULATED_CRASH)

        assertOnlyReportedAsFatalIssue()
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
