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
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.session.SessionStrategy
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
    private val preferences = MockPreferences()
    private val lifecycleOwner = mock<LifecycleOwner>()

    private val systemDateProvider =
        DateProvider {
            Date(TEST_DATE_TIMESTAMP)
        }

    private lateinit var logger: LoggerImpl
    private var testServerPort: Int? = null

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        CaptureJniLibrary.load()
        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)

        val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        doReturn(lifecycleRegistry).whenever(lifecycleOwner).lifecycle
    }

    @After
    fun teardown() {
        CaptureTestJniLibrary.stopTestApiServer()
    }

    private fun contextWithAppVersion(
        version: String,
        appVersionCode: Int,
    ): android.content.Context {
        val packageName = ContextHolder.APP_CONTEXT.packageName

        // Mock package manager to return old version
        val packageManager = mock<PackageManager>()
        val packageInfo =
            PackageInfo().apply {
                versionName = version
                @Suppress("DEPRECATION")
                versionCode = appVersionCode
            }
        whenever(packageManager.getPackageInfo(packageName, 0)).thenReturn(packageInfo)

        // Use a spy to avoid mocking every system service (Battery, Window, etc.)
        val context = com.nhaarman.mockitokotlin2.spy(ApplicationProvider.getApplicationContext<android.content.Context>())
        whenever(context.packageManager).thenReturn(packageManager)
        // We still need to mock ActivityManager to control getHistoricalProcessExitReasons
        whenever(context.getSystemService(android.content.Context.ACTIVITY_SERVICE)).thenReturn(activityManager)

        return context
    }

    @Test
    fun testSessionIdOverride() {
        val oldAppVersion = "1.2.3"
        val oldAppVersionCode = 123
        val newAppVersion = "1.2.4"
        val newAppVersionCode = 124

        val context = contextWithAppVersion(oldAppVersion, oldAppVersionCode)
        val clientAttributes = ClientAttributes(context, lifecycleOwner)

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
                clientAttributes = clientAttributes,
            )

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

        val newContext = contextWithAppVersion(newAppVersion, newAppVersionCode)
        val newClientAttributes = ClientAttributes(newContext, lifecycleOwner)

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

        val testLog =
            generateSequence { CaptureTestJniLibrary.nextUploadedLog() }
                .take(10)
                .first { it.message == "test log" }

        assertThat(testLog.sessionId).isEqualTo("bar")
        assertThat(testLog.fields["app_version"]).isEqualTo(FieldValue.StringField(newAppVersion))
        assertThat(testLog.fields["_app_version_code"]).isEqualTo(FieldValue.StringField(newAppVersionCode.toString()))
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("localhost")
            .port(testServerPort!!)
            .build()
}
