// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.TargetApi
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.threading.CaptureDispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

// This should return "2022-07-05T18:55:58.123Z" when formatted.
private const val TEST_DATE_TIMESTAMP: Long = 1657047358123

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@TargetApi(Build.VERSION_CODES.R)
class CaptureLoggerFeatureFlagsCrashReportTest {
    private val systemDateProvider =
        DateProvider {
            Date(TEST_DATE_TIMESTAMP)
        }

    private lateinit var testServer: TestApiServer

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        CaptureJniLibrary.load()
        testServer = TestApiServer()

        // Enable crash reporting by creating the config file.
        // In production, this config is delivered from the server via runtime configuration
        // and written by ConfigWriter. In tests, we create it manually since the async
        // ConfigWriter may not complete in time with the synchronous test executor.
        val reportsDir = java.io.File(ContextHolder.APP_CONTEXT.filesDir, "bitdrift_capture/reports/")
        reportsDir.mkdirs()
        val configFile = java.io.File(reportsDir, "config.csv")
        configFile.writeText("crash_reporting.enabled,true")
    }

    @After
    fun teardown() {
        testServer.stop()
    }

    /**
     * Helper function to run a logger instance with automatic cleanup.
     * The logger is automatically shut down after the block executes.
     */
    private fun <T> withLogger(
        sessionId: String,
        preferences: MockPreferences,
        block: (LoggerImpl) -> T,
    ): T {
        val logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServer.url,
                fieldProviders = listOf(),
                dateProvider = systemDateProvider,
                sessionStrategy = SessionStrategy.Fixed { sessionId },
                configuration = Configuration(),
                context = ContextHolder.APP_CONTEXT,
                preferences = preferences,
                fatalIssueReporter = FatalIssueReporter(dateProvider = FakeDateProvider),
            )
        return try {
            block(logger)
        } finally {
            CaptureJniLibrary.shutdown(logger.loggerId)
        }
    }

    /**
     * Verifies that feature flags set before a crash are persisted and included in the
     * crash report uploaded on the next app start.
     *
     * Test flow:
     * 1. First app start: Initialize logger to establish configuration
     * 2. Second app start: Initialize logger, set feature flags, simulate crash
     * 3. Third app start: Initialize logger, verify crash report contains feature flags
     */
    @Test
    fun testFeatureFlagsPersistedInCrashReport() {
        val preferences = MockPreferences()

        // Phase 1: Start the logger and process configuration
        withLogger("session1", preferences) { logger ->
            val stream = testServer.awaitNextStream()
            stream.configureAggressiveUploads()

            // Write a blocking log to ensure config delivery gets persisted
            logger.log(
                LogType.NORMAL,
                LogLevel.INFO,
                blocking = true,
            ) { "config_init" }
            testServer.nextUploadedLog()
        }

        // Phase 2: Second app start - set feature flags and simulate crash
        withLogger("session2", preferences) { logger ->
            val stream = testServer.awaitNextStream()
            stream.configureAggressiveUploads()

            // Set feature flags before the crash
            logger.setFeatureFlagExposure("dark_mode", "enabled")
            logger.setFeatureFlagExposure("new_ui", "variant_b")
            logger.setFeatureFlagExposure("experimental_feature", null)

            // Simulate a crash by calling persistJvmCrash on the issue processor
            val processor = logger.getIssueProcessor()
            assertThat(processor).isNotNull

            val crashingThread = Thread.currentThread()
            val exception = RuntimeException("Test crash for feature flags verification")
            processor!!.persistJvmCrash(crashingThread, exception, null)
        }

        // Phase 3: Third app start - verify crash report contains feature flags
        withLogger("session3", preferences) { _ ->
            val stream = testServer.awaitNextStream()
            stream.configureAggressiveUploads()

            // The crash report should be uploaded as an artifact
            val uploadedReport = testServer.nextUploadedReport()

            // Verify the session ID matches the session where the crash occurred
            assertThat(uploadedReport.sessionId).isEqualTo("session2")

            // Verify the feature flags from the upload request
            assertThat(uploadedReport.featureFlags).hasSize(3)
            assertThat(uploadedReport.featureFlags).containsEntry("dark_mode", "enabled")
            assertThat(uploadedReport.featureFlags).containsEntry("new_ui", "variant_b")
            assertThat(uploadedReport.featureFlags).containsEntry("experimental_feature", null)

            // Verify the crash report contains the error information
            assertThat(uploadedReport.report.errorsLength).isGreaterThan(0)
            val error = uploadedReport.report.errors(0)
            assertThat(error).isNotNull
            assertThat(error!!.reason).contains("Test crash for feature flags verification")
            assertThat(error.name).isEqualTo("java.lang.RuntimeException")
        }
    }
}
