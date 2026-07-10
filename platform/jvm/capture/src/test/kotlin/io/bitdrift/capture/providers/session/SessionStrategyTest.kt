// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.LoggerImpl
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SessionStrategyTest {
    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        CaptureJniLibrary.load()
    }

    @Test
    fun fixedSessionStrategy() {
        val generatedSessionIds = mutableListOf<String>()

        Capture.Logger.start(
            apiKey = "test",
            apiUrl = testServerUrl(),
            fieldProviders = listOf(),
            dateProvider = mock(),
            context = ContextHolder.APP_CONTEXT,
            sessionStrategy =
                SessionStrategy.Fixed {
                    val sessionId = UUID.randomUUID().toString()
                    generatedSessionIds.add(sessionId)
                    sessionId
                },
            configuration = Configuration(),
        )

        val logger = Capture.logger()
        val sessionId = logger?.sessionId

        assertThat(generatedSessionIds.count()).isEqualTo(1)
        assertThat(generatedSessionIds[0]).isEqualTo(sessionId)

        logger?.startNewSession()
        val newSessionId = logger?.sessionId

        assertThat(generatedSessionIds.count()).isEqualTo(2)
        assertThat(generatedSessionIds[1]).isEqualTo(newSessionId)
        assertThat(sessionId).isNotEqualTo(newSessionId)
    }

    @Test
    fun activityBasedSessionStrategy() {
        var observedSessionId: String? = null

        val logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = mock(),
                context = ContextHolder.APP_CONTEXT,
                sessionStrategy =
                    SessionStrategy.ActivityBased {
                        observedSessionId = it
                    },
                configuration = Configuration(),
                preferences = mock(),
            )

        val sessionId = logger.sessionId
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(sessionId).isEqualTo(observedSessionId)

        logger.startNewSession()
        val newSessionId = logger.sessionId

        assertThat(sessionId).isNotEqualTo(newSessionId)
    }

    @Test
    fun activityBasedSessionStrategy_callbackRunsOnMainThread() {
        var callbackLooper: Looper? = null

        val logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = mock(),
                context = ContextHolder.APP_CONTEXT,
                sessionStrategy =
                    SessionStrategy.ActivityBased {
                        callbackLooper = Looper.myLooper()
                    },
                configuration = Configuration(),
                preferences = mock(),
            )

        logger.sessionId
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(callbackLooper).isEqualTo(Looper.getMainLooper())
    }

    @Test
    fun activityBasedSessionStrategy_sessionIdChangedEmitsOnMainThread() {
        var observedSessionId: String? = null
        var callbackLooper: Looper? = null
        val newSessionId = "test-session-123"
        val activityBasedConfig =
            SessionStrategyConfiguration.ActivityBased(
                sessionStrategy =
                    SessionStrategy.ActivityBased { sessionId ->
                        observedSessionId = sessionId
                        callbackLooper = Looper.myLooper()
                    },
            )

        // Call from a background thread to simulate the bd-tokio/JNI thread invoking the callback.
        Thread { activityBasedConfig.sessionIdChanged(newSessionId) }.apply {
            start()
            join()
        }

        shadowOf(Looper.getMainLooper()).idle()
        assertThat(observedSessionId).isEqualTo(newSessionId)
        assertThat(callbackLooper).isEqualTo(Looper.getMainLooper())
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("test.bitdrift.com")
            .build()
}
