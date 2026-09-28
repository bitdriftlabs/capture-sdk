// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ApplicationExitInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionConfiguration
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.exitinfo.ExitReason
import io.bitdrift.capture.reports.exitinfo.PreviousRunInfoResolver
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.DebugCustomerCallbackException
import io.bitdrift.capture.utils.assertPreviousRunInfo
import io.bitdrift.capture.utils.setIsDebuggable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Suppress("DEPRECATION")
class CaptureTest {
    private val latestAppExitInfoProvider = FakeLatestAppExitInfoProvider()
    private val preferences = MockPreferences()
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()

    @Before
    fun tearDown() {
        latestAppExitInfoProvider.reset()
        Logger.resetShared()
    }

    // This Test needs to run first since the following tests need to initialize
    // the ContextHolder before they can run.
    @Test
    fun aConfigureSkipsLoggerCreationWhenContextNotInitialized() {
        assertThat(Capture.logger()).isNull()

        Logger.start(
            apiKey = "test1",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
        )

        assertThat(Capture.logger()).isNull()
    }

    @Test
    fun aStart_withNullContext_emitsFailureCallback() {
        var capturedResult: CaptureResult<ILogger>? = null

        Logger.start(
            apiKey = "test1",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            context = null,
        ) { result ->
            capturedResult = result
        }

        assertThat(capturedResult).isInstanceOf(CaptureResult.Failure::class.java)
        val failure = capturedResult as CaptureResult.Failure
        assertThat(failure.error).isInstanceOf(SdkStartFailure::class.java)
        assertThat(failure.error.message).contains("null context")
    }

    @Test
    fun aStart_withInitExecutorAndNullContext_emitsFailureWithoutScheduling() {
        var capturedResult: CaptureResult<ILogger>? = null
        var executeCallCount = 0

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            bridge = mock(IBridge::class.java),
            context = null,
            initSdkExecutor = Executor { executeCallCount++ },
        ) { result ->
            capturedResult = result
        }

        assertThat(executeCallCount).isEqualTo(0)
        val failure = capturedResult as CaptureResult.Failure
        assertThat(failure.error.message).contains("null context")
    }

    // Accessing fields prior to the configuration of the logger may lead to crash since it can
    // potentially call into a native method that's used to sanitize passed url path.
    @Test
    fun bDoesNotAccessFieldsIfLoggerNotConfigured() {
        assertThat(Capture.logger()).isNull()

        val requestInfo = HttpRequestInfo("GET", path = HttpUrlPath("/foo/12345"))
        Logger.log(requestInfo)

        val responseInfo =
            HttpResponseInfo(
                requestInfo,
                response =
                    HttpResponse(
                        result = HttpResponse.HttpResult.SUCCESS,
                        path = HttpUrlPath("/foo_path/12345"),
                    ),
                durationMs = 60L,
            )
        Logger.log(responseInfo)

        assertThat(Capture.logger()).isNull()
    }

    @Test
    @OptIn(ExperimentalBitdriftApi::class)
    fun bGetPreviousRunInfoNoopWhenLoggerNotConfigured() {
        assertThat(Capture.logger()).isNull()
        assertThat(Logger.getPreviousRunInfo()).isNull()
    }

    @Test
    fun cIdempotentConfigure() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        assertThat(Capture.logger()).isNull()

        var capturedResult: CaptureResult<ILogger>? = null

        Logger.start(
            apiKey = "test1",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
        ) { result ->
            capturedResult = result
        }

        val status = Logger.getSdkStatus()
        assertThat(status.initializationState).isNotEqualTo(InitializationState.NOT_STARTED)

        val logger = Capture.logger()
        assertThat(logger).isNotNull()
        assertThat(Logger.deviceId).isNotNull()

        // Verify completion callback emits success with expected properties.
        assertThat(capturedResult).isInstanceOf(CaptureResult.Success::class.java)
        val success = capturedResult as CaptureResult.Success
        assertThat(success.value.sessionId).isNotEmpty()
        assertThat(success.value.sessionUrl).isNotEmpty()
        assertThat(success.value.deviceId).isNotEmpty()

        Logger.start(
            apiKey = "test2",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
        )

        // Calling reconfigure a second time does not change the static logger.
        assertThat(logger).isEqualTo(Capture.logger())
    }

    @Test
    fun startAsync_returnsBeforeInitAndDeliversResultOnMainThread() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        val backgroundBlocker = CountDownLatch(1)
        CaptureDispatchers.CommonBackground.runAsync { backgroundBlocker.await(5, TimeUnit.SECONDS) }
        var capturedResult: CaptureResult<ILogger>? = null
        var startResultThread: Thread? = null

        Logger.startAsync(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            initialFields = emptyMap(),
        ) { result ->
            capturedResult = result
            startResultThread = Thread.currentThread()
        }

        assertThat(Capture.logger()).isInstanceOf(PreInitInMemoryLogger::class.java)
        assertThat(Logger.getSdkStatus().initializationState).isEqualTo(InitializationState.STARTING)
        Logger.startNewSession("buffered-session-id")

        backgroundBlocker.countDown()

        awaitOnMainLooper { capturedResult != null }
        assertThat(capturedResult).isInstanceOf(CaptureResult.Success::class.java)
        assertThat(startResultThread).isEqualTo(Looper.getMainLooper().thread)
        assertThat(Capture.logger()).isInstanceOf(LoggerImpl::class.java)
        assertThat(Logger.sessionId).isEqualTo("buffered-session-id")
        assertThat(Logger.getSdkStatus().initializationState).isNotIn(
            InitializationState.NOT_STARTED,
            InitializationState.STARTING,
        )
    }

    @Test
    fun start_withRejectingInitExecutor_emitsFailureAndAllowsRetry() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        var capturedResult: CaptureResult<ILogger>? = null

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            bridge = CaptureJniLibrary,
            initSdkExecutor = Executor { throw RejectedExecutionException("rejected") },
        ) { result ->
            capturedResult = result
        }

        val failure = capturedResult as CaptureResult.Failure
        assertThat(failure.error.message).contains("rejected")
        assertThat(Capture.logger()).isNull()
        assertThat(Logger.getSdkStatus().initializationState).isEqualTo(InitializationState.NOT_STARTED)

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            bridge = CaptureJniLibrary,
        ) { result ->
            capturedResult = result
        }

        assertThat(capturedResult).isInstanceOf(CaptureResult.Success::class.java)
        assertThat(Capture.logger()).isInstanceOf(LoggerImpl::class.java)
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsCrashForCrashReasons() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH)

        val previousRunInfo = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertPreviousRunInfo(previousRunInfo, hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash)
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNoCrashForExitSelfReason() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_EXIT_SELF)

        val previousRunInfo = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertPreviousRunInfo(previousRunInfo, hasFatallyTerminated = false, terminationReason = ExitReason.ExitSelf)
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNoCrashWhenNoExitInfo() {
        latestAppExitInfoProvider.setAsEmptyReason()

        val previousRunInfo = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertPreviousRunInfo(previousRunInfo, hasFatallyTerminated = false, terminationReason = null)
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNullWhenProviderFails() {
        latestAppExitInfoProvider.setAsErrorResult()

        val previousRunInfo = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(previousRunInfo).isNull()
    }

    @Test
    fun getSdkStatus_beforeStart_returnsNotStarted() {
        val status = Logger.getSdkStatus()

        assertThat(status.initializationState).isEqualTo(InitializationState.NOT_STARTED)
        assertThat(status.lastHandshakeTimeMs).isNull()
        assertThat(status.lastConfigDeliveryTimeMs).isNull()
    }

    @Test
    fun startResultCallback_throwingException_onDebugBuild_throwsDebugCustomerCallbackException() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        setIsDebuggable(debuggable = true)

        assertThatThrownBy {
            Logger.start(
                apiKey = "test1",
                initialFields = emptyMap(),
                sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
                dateProvider = null,
            ) { _ ->
                throw IllegalStateException("customer callback error")
            }
        }.isInstanceOf(DebugCustomerCallbackException::class.java)
            .hasCauseInstanceOf(IllegalStateException::class.java)

        assertThat(Capture.logger()).isNotNull()
    }

    @Test
    fun startResultCallback_throwingException_onReleaseBuild_swallowsAndSdkRemainsStarted() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        setIsDebuggable(debuggable = false)

        Logger.start(
            apiKey = "test1",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
        ) { _ ->
            throw IllegalStateException("customer callback error")
        }

        assertThat(Capture.logger()).isNotNull()
        val status = Logger.getSdkStatus()
        assertThat(status.initializationState).isNotEqualTo(InitializationState.NOT_STARTED)
    }

    private fun awaitOnMainLooper(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertThat(condition()).isTrue()
    }
}
