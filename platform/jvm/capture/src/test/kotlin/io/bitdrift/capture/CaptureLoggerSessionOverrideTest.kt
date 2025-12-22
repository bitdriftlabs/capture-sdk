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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.IssueReporter
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    private val issueReporter =
        IssueReporter(
            dateProvider = FakeDateProvider,
        )

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

    @Test
    fun testSessionIdOverride() {
        val oldAppVersion = "1.2.3"
        val oldAppVersionCode = 123L
        val newAppVersion = "1.2.4"
        val newAppVersionCode = 124L
        val packageName = ContextHolder.APP_CONTEXT.packageName

        // Mock package manager to return old version
        val packageManager = mock<PackageManager>()
        val packageInfo =
            PackageInfo().apply {
                versionName = oldAppVersion
                @Suppress("DEPRECATION")
                versionCode = oldAppVersionCode.toInt()
            }
        whenever(packageManager.getPackageInfo(packageName, 0)).thenReturn(packageInfo)

        // Use a spy to avoid mocking every system service (Battery, Window, etc.)
        val context = com.nhaarman.mockitokotlin2.spy(ApplicationProvider.getApplicationContext<android.content.Context>())
        whenever(context.packageManager).thenReturn(packageManager)
        // We still need to mock ActivityManager to control getHistoricalProcessExitReasons
        whenever(context.getSystemService(android.content.Context.ACTIVITY_SERVICE)).thenReturn(activityManager)

        val lifecycleOwner = mock<LifecycleOwner>()
        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        doReturn(lifecycleRegistry).whenever(lifecycleOwner).lifecycle

        val clientAttributes = ClientAttributes(context, lifecycleOwner)
        // Force initial attributes population
        clientAttributes.invoke()

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
                configuration = Configuration(sessionReplayConfiguration = null),
                context = context,
                preferences = preferences,
                activityManager = activityManager,
                issueReporter = issueReporter,
                clientAttributes = clientAttributes,
            )

        // Force attributes update again after logger creation
        clientAttributes.invoke()

        // Let the first logger process come up and send it the configuration to aggressively upload
        // the data. On the second startup it should read this configuration from cache.
        val firstApiStreamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(firstApiStreamId).isNotEqualTo(-1)
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(firstApiStreamId)

        // Ensure session data is persisted
        logger.flush(true)

        val timestamp = TEST_DATE_TIMESTAMP + 100L
        val mockExitInfo = mock<ApplicationExitInfo>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(mockExitInfo.timestamp).thenReturn(timestamp)
        whenever(mockExitInfo.processName).thenReturn(ContextHolder.APP_CONTEXT.packageName)
        whenever(mockExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_ANR)
        whenever(mockExitInfo.importance).thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        whenever(mockExitInfo.status).thenReturn(0)
        whenever(mockExitInfo.pss).thenReturn(1)
        whenever(mockExitInfo.rss).thenReturn(2)
        whenever(mockExitInfo.description).thenReturn("test-description")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        // We need to shut down the logger first before starting a new one, otherwise the new one fails to initialize due to the flock
        // on the ring buffer.
        CaptureJniLibrary.shutdown(logger.loggerId)

        // Update mock package manager to return new version
        val newPackageInfo =
            PackageInfo().apply {
                versionName = newAppVersion
                @Suppress("DEPRECATION")
                versionCode = newAppVersionCode.toInt()
            }
        whenever(packageManager.getPackageInfo(packageName, 0)).thenReturn(newPackageInfo)

        val newClientAttributes = ClientAttributes(context, lifecycleOwner)

        // Start another logger instance. Notice how its session strategy specifies "bar"
        // session Id.
        logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = systemDateProvider,
                sessionStrategy = SessionStrategy.Fixed { "bar" },
                configuration = Configuration(sessionReplayConfiguration = null),
                context = context,
                preferences = preferences,
                activityManager = activityManager,
                issueReporter = issueReporter,
                clientAttributes = newClientAttributes,
            )

        val secondApiStreamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(secondApiStreamId).isNotEqualTo(-1)
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(secondApiStreamId)

        var appExitLogFound = false
        while (!appExitLogFound) {
            val log = CaptureTestJniLibrary.nextUploadedLog()
            if (log.message == "AppExit") {
                assertThat(log.level).isEqualTo(LogLevel.ERROR.value)
                assertThat(log.sessionId).isEqualTo("foo")
                assertThat(log.rfc3339Timestamp).isEqualTo("2022-07-05T18:55:58.223Z")
                assertThat(log.fields["app_version"]).isEqualTo(FieldValue.StringField(oldAppVersion))
                assertThat(log.fields["_app_version_code"]).isEqualTo(FieldValue.StringField(oldAppVersionCode.toString()))
                appExitLogFound = true
            }
        }

        logger.log(LogLevel.INFO, null, null) { "test log" }

        var nextLogFound = false
        while (!nextLogFound) {
            val log = CaptureTestJniLibrary.nextUploadedLog()
            if (log.message == "test log") {
                assertThat(log.sessionId).isEqualTo("bar")
                assertThat(log.fields["app_version"]).isEqualTo(FieldValue.StringField(newAppVersion))
                assertThat(log.fields["_app_version_code"]).isEqualTo(FieldValue.StringField(newAppVersionCode.toString()))
                nextLogFound = true
            }
        }
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("localhost")
            .port(testServerPort!!)
            .build()
}
