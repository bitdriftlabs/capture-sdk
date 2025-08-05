// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.IFatalIssueReporter
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SessionStrategyTest {
    private val fatalIssueReporter: IFatalIssueReporter = FatalIssueReporter()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
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
        val strategyLatch = CountDownLatch(1)
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
                        strategyLatch.countDown()
                    },
                configuration = Configuration(),
                preferences = mock(),
                fatalIssueReporter = fatalIssueReporter,
            )

        val sessionId = logger.sessionId
        strategyLatch.await()

        assertThat(sessionId).isEqualTo(observedSessionId)

        logger.startNewSession()
        val newSessionId = logger.sessionId

        assertThat(sessionId).isNotEqualTo(newSessionId)
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("test.bitdrift.com")
            .build()
}
