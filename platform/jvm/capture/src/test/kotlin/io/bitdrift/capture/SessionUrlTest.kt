// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.IFatalIssueReporter
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SessionUrlTest {
    private val fatalIssueReporter: IFatalIssueReporter = FatalIssueReporter()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testDefaultSessionUrl() {
        val logger = createLogger("https://api.bitdrift.io")
        assertThat(logger.sessionUrl).isEqualTo("https://timeline.bitdrift.io/s/${logger.sessionId}?utm_source=sdk")
    }

    @Test
    fun testCustomSessionUrlWithPath() {
        val logger = createLogger("https://api.api.mycompany.bitdrift.io/v1/path")
        assertThat(logger.sessionUrl).isEqualTo("https://timeline.api.mycompany.bitdrift.io/s/${logger.sessionId}?utm_source=sdk")
    }

    @Test
    fun testCustomSessionUrl1() {
        val logger = createLogger("https://api.api.mycompany.bitdrift.io")
        assertThat(logger.sessionUrl).isEqualTo("https://timeline.api.mycompany.bitdrift.io/s/${logger.sessionId}?utm_source=sdk")
    }

    @Test
    fun testCustomSessionUrl2() {
        val logger = createLogger("https://api.myapicompany.bitdrift.io")
        assertThat(logger.sessionUrl).isEqualTo("https://timeline.myapicompany.bitdrift.io/s/${logger.sessionId}?utm_source=sdk")
    }

    @Test
    fun testCustomSessionUrl3() {
        val logger = createLogger("https://api.companyapi.bitdrift.io")
        assertThat(logger.sessionUrl).isEqualTo("https://timeline.companyapi.bitdrift.io/s/${logger.sessionId}?utm_source=sdk")
    }

    @Test
    fun testInvalidApiUrl() {
        val logger = createLogger("https://mycustomapiurl.com")
        assertThat(logger.sessionUrl).isEqualTo("https://mycustomapiurl.com/s/${logger.sessionId}?utm_source=sdk")
    }

    private fun createLogger(apiUrl: String): LoggerImpl =
        LoggerImpl(
            apiKey = "test",
            apiUrl = apiUrl.toHttpUrl(),
            configuration = Configuration(),
            fieldProviders = listOf(),
            context = ContextHolder.APP_CONTEXT,
            dateProvider = SystemDateProvider(),
            sessionStrategy = SessionStrategy.Fixed { "SESSION_ID" },
            fatalIssueReporter = fatalIssueReporter,
        )
}
