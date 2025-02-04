// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.network.okhttp.OkHttpNetwork
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.session.SessionStrategyConfiguration
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

class CaptureLoggerNetworkTest {
    init {
        CaptureJniLibrary.load()
    }

    @Rule
    @JvmField
    var directory = TemporaryFolder()

    private var pingIdleTimeout: Int = -1
    private var streamTimeoutSeconds: Long = 1
    private var logger: Long? = null
    private var testServerPort: Int? = null

    class TestMetadataProvider : IMetadataProvider {
        override fun timestamp(): Long = Date().time

        override fun ootbFields(): InternalFieldsList = listOf()

        override fun customFields(): InternalFieldsList = listOf()
    }

    companion object {
        val loggerBridge: TestMetadataProvider = TestMetadataProvider()
    }

    @Before
    fun setUp() {
        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
    }

    @After
    fun tearDown() {
        if (testServerPort != null) {
            CaptureTestJniLibrary.stopTestApiServer()
        }
        if (logger != null) {
            CaptureJniLibrary.destroyLogger(logger!!)
        }
    }

    private fun createLogger(): Long {
        testServerPort = CaptureTestJniLibrary.startTestApiServer(pingIdleTimeout)

        val network =
            OkHttpNetwork(
                apiBaseUrl = testServerUrl(testServerPort!!),
                timeoutSeconds = streamTimeoutSeconds,
            )

        val logger =
            CaptureJniLibrary.createLogger(
                directory.newFolder().path,
                apiKey = "abc123",
                SessionStrategy.Fixed().createSessionStrategyConfiguration { },
                loggerBridge,
                mock(),
                mock(),
                mock(),
                "test",
                "test",
                "test",
                network,
                mock(),
                mock(),
            )
        CaptureJniLibrary.startLogger(logger)
        return logger
    }

    @Test
    fun `okhttp happy path with timeout`() {
        createLogger()

        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        assertThat(CaptureTestJniLibrary.awaitApiServerReceivedHandshake(streamId)).isTrue

        // Wait for the idle timeout to hit, after which we should get
        // another handshake due to the stream being reestablished.
        assertThat(CaptureTestJniLibrary.awaitApiServerStreamClosed(streamId, 5000)).isTrue()

        val secondStreamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(secondStreamId).isNotEqualTo(-1)
        assertThat(CaptureTestJniLibrary.awaitApiServerReceivedHandshake(secondStreamId)).isTrue
    }

    @Test
    fun `okhttp happy path with configuration update`() {
        createLogger()

        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        assertThat(CaptureTestJniLibrary.awaitApiServerReceivedHandshake(streamId)).isTrue

        CaptureTestJniLibrary.sendConfigurationUpdate(streamId)
        CaptureTestJniLibrary.awaitConfigurationAck(streamId, 500)
    }

    @Test
    fun `okhttp happy path with ping keep alive`() {
        // We set up a scenario with a 500ms keep alive interval and a 1,000ms OkHttp idle timeout.
        // In this case the keep alive should be sufficient to maintain the stream way beyond the
        // OkHttp idle timeout.
        pingIdleTimeout = 500

        createLogger()

        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        assertThat(CaptureTestJniLibrary.awaitApiServerReceivedHandshake(streamId)).isTrue

        // Wait for two seconds to see if we hit any idle timeouts - the pings should keep
        // the stream alive so this should not happen.
        assertThat(CaptureTestJniLibrary.awaitApiServerStreamClosed(streamId, 2000)).isFalse
    }

    @Test
    fun `okhttp network server not available`() {
        // We start the logger without starting the test server, so any attempt at connecting
        // to it should immediately fail (connection refused).
        val network =
            OkHttpNetwork(
                apiBaseUrl = testServerUrl(50051),
                timeoutSeconds = 1,
            )
        val loggerId =
            CaptureJniLibrary.createLogger(
                directory.newFolder().path,
                apiKey = "abc123",
                SessionStrategyConfiguration.Fixed(
                    sessionStrategy = SessionStrategy.Fixed(),
                    onSessionIdChanged = { },
                ),
                loggerBridge,
                mock(),
                mock(),
                mock(),
                "test",
                "test",
                "test",
                network,
                mock(),
                mock(),
            )
        CaptureJniLibrary.startLogger(loggerId)
        logger = loggerId

        // TODO(snowp): Once we expose some callbacks here we can avoid a blind sleep.
        Thread.sleep(1000)
    }

    @Test
    fun large_upload() {
        val port = CaptureTestJniLibrary.startTestApiServer(500)
        val network =
            OkHttpNetwork(
                apiBaseUrl = testServerUrl(port),
                timeoutSeconds = 1,
            )

        val logger =
            CaptureJniLibrary.createLogger(
                directory.newFolder().path,
                apiKey = "abc123",
                SessionStrategy.Fixed().createSessionStrategyConfiguration { },
                loggerBridge,
                mock(),
                mock(),
                mock(),
                "test",
                "test",
                "test",
                network,
                // this test fails if we pass mock() in here. It has something to do with
                // jni trying to call methods on Mockito mocks.
                MockPreferences(),
                mock(),
            )
        CaptureJniLibrary.startLogger(logger)

        CaptureTestJniLibrary.runLargeUploadTest(logger)

        CaptureJniLibrary.destroyLogger(logger)
    }

    private fun testServerUrl(port: Int): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("localhost")
            .port(port)
            .build()
}
