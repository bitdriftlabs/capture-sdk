// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.LoggerState
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IssueCallbackConfigurationTest {
    private lateinit var appContext: Context

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        val initializer = ContextHolder()
        initializer.create(appContext)
    }

    @After
    fun tearDown() {
        Capture.Logger.resetShared()
    }

    @Test
    fun dispatchCallback_withCustomerExceptionThrown_shouldReportInternalLog() {
        val expectedException = IllegalStateException("forced callback failure")
        val callback =
            IssueReportCallback {
                throw expectedException
            }
        val issueCallbackConfiguration =
            IssueCallbackConfiguration(
                executor = MoreExecutors.newDirectExecutorService(),
                issueReportCallback = callback,
            )
        val spyLogger = startSdkAndReturnLogger(issueCallbackConfiguration)

        issueCallbackConfiguration.dispatch(report = buildFakeReport())

        val throwableCaptor = argumentCaptor<Throwable>()
        val messageCaptor = argumentCaptor<() -> String>()
        verify(spyLogger).logInternalError(
            throwableCaptor.capture(),
            eq(false),
            messageCaptor.capture(),
        )
        assertThat(throwableCaptor.firstValue).isSameAs(expectedException)
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Issue report callback failed")
    }

    @Test
    fun dispatchCallback_withoutCustomerExceptionThrown_shouldNotReportInternalLog() {
        val issueCallbackConfiguration =
            IssueCallbackConfiguration(
                executor = MoreExecutors.newDirectExecutorService(),
                issueReportCallback = { true },
            )
        val spyLogger = startSdkAndReturnLogger(issueCallbackConfiguration)

        issueCallbackConfiguration.dispatch(report = buildFakeReport())

        verify(spyLogger, never()).logInternalError(
            any(),
            any(),
            any(),
        )
    }

    private fun buildFakeReport(): Report =
        Report(
            reportType = "Crash",
            reason = "java.lang.RuntimeException",
            details = "Forced unhandled exception",
            sessionId = "session-id",
            fields = mapOf("test" to "true"),
        )

    private fun startSdkAndReturnLogger(issueCallbackConfiguration: IssueCallbackConfiguration): IInternalLogger {
        Capture.Logger.start(
            apiKey = "test",
            sessionStrategy = SessionStrategy.Fixed(),
            configuration = Configuration(issueCallbackConfiguration = issueCallbackConfiguration),
            dateProvider = SystemDateProvider(),
            context = appContext,
        )

        val spyLogger = spy(Capture.logger() as LoggerImpl)

        val field = Capture::class.java.getDeclaredField("default")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateRef =
            field.get(Capture) as java.util.concurrent.atomic.AtomicReference<LoggerState>
        stateRef.set(LoggerState.Started(spyLogger))
        return spyLogger
    }
}
