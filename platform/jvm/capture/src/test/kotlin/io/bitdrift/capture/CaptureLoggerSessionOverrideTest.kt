// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.fakes.FakePreInitLogFlusher
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.util.Date

// This should return "2022-07-05T18:55:58.123Z" when formatted.
private const val TEST_DATE_TIMESTAMP: Long = 1657047358123

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@TargetApi(Build.VERSION_CODES.R)
class CaptureLoggerSessionOverrideTest {
    private val activityManager: ActivityManager = mock()

    private val systemDateProvider =
        DateProvider {
            Date(TEST_DATE_TIMESTAMP)
        }

    private lateinit var logger: LoggerImpl
    private var testServerPort: Int? = null
    private val fatalIssueReporter = FatalIssueReporter()
    private val preInitLogFlusher = FakePreInitLogFlusher()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        CaptureJniLibrary.load()
        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)
    }

    @After
    fun teardown() {
        CaptureTestJniLibrary.stopTestApiServer()
    }

    /**
     *  Verify that upon the launch of the SDK it's possible to emit logs with session Id
     *  equal to the last active session ID from the previous run of the SDK.
     */
    @Ignore("TODO(FranAguilera): BIT-5484. This works on gradle with Roboelectric. Fix on bazel")
    @Test
    fun testSessionIdOverride() {
        // Start the logger and process one log with it just so that
        // it generates a session Id that's stored in passed `Preferences` instance.
        val preferences = MockPreferences()

        logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = systemDateProvider,
                sessionStrategy = SessionStrategy.Fixed { "foo" },
                configuration = Configuration(),
                context = ContextHolder.APP_CONTEXT,
                preferences = preferences,
                fatalIssueReporter = fatalIssueReporter,
                preInitLogFlusher = preInitLogFlusher,
            )

        CaptureTestJniLibrary.stopTestApiServer()

        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)

        val sessionId = "foo"
        val timestamp = 123L
        val mockExitInfo = mock<ApplicationExitInfo>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(mockExitInfo.processStateSummary).thenReturn(sessionId.toByteArray(StandardCharsets.UTF_8))
        whenever(mockExitInfo.timestamp).thenReturn(timestamp)
        whenever(mockExitInfo.processName).thenReturn("test-process-name")
        whenever(mockExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_ANR)
        whenever(mockExitInfo.importance).thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        whenever(mockExitInfo.status).thenReturn(0)
        whenever(mockExitInfo.pss).thenReturn(1)
        whenever(mockExitInfo.rss).thenReturn(2)
        whenever(mockExitInfo.description).thenReturn("test-description")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        // Start another logger instance. Notice how its session strategy specifies "bar"
        // session Id.
        logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = systemDateProvider,
                sessionStrategy = SessionStrategy.Fixed { "bar" },
                configuration = Configuration(),
                context = ContextHolder.APP_CONTEXT,
                preferences = preferences,
                activityManager = activityManager,
                fatalIssueReporter = fatalIssueReporter,
                preInitLogFlusher = preInitLogFlusher,
            )
        val newStreamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(newStreamId).isNotEqualTo(-1)

        CaptureTestJniLibrary.configureAggressiveContinuousUploads(newStreamId)

        val log = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(log.message).isEqualTo("AppExit")
        assertThat(log.level).isEqualTo(LogLevel.ERROR.value)
        assertThat(log.sessionId).isEqualTo("foo")
        assertThat(log.rfc3339Timestamp).isEqualTo("1970-01-01T00:00:00.123Z")

        val sdkConfigured = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(sdkConfigured.message).isEqualTo("SDKConfigured")
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("localhost")
            .port(testServerPort!!)
            .build()
}
