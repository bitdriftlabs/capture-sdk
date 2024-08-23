// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.bitdrift.capture.network.okhttp.HttpApiEndpoint
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OkHttpApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var apiClient: OkHttpApiClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        apiClient = OkHttpApiClient(server.url(""), "api-key")
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun verifyCorrectRequestAndResponse() {
        // ARRANGE
        server.enqueue(MockResponse().setBody("{\"code\":\"123456\"}"))
        val okHttpClientLatch = CountDownLatch(1)

        // ACT
        val typedRequest = DeviceCodeRequest("device-id")
        var apiResult: CaptureResult<DeviceCodeResponse>? = null
        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(
            HttpApiEndpoint.GetTemporaryDeviceCode,
            typedRequest,
            mapOf("x-custom-header" to "header-value"),
        ) { result ->
            apiResult = result
            okHttpClientLatch.countDown()
        }

        // ASSERT
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(request?.path).isEqualTo("/v1/device/code")
        assertThat(request?.headers?.get("content-type")).isEqualTo("application/json; charset=utf-8")
        assertThat(request?.method).isEqualTo("POST")
        assertThat(request?.headers).contains(Pair("x-bitdrift-api-key", "api-key"))
        assertThat(request?.headers).contains(Pair("x-custom-header", "header-value"))
        val jsonPayload = request?.body?.readString(Charset.defaultCharset()).orEmpty()
        assertThat(jsonPayload).isEqualTo("{\"device_id\":\"device-id\"}")

        assertThat(apiResult?.unwrap()?.code).isEqualTo("123456")
    }

    @Test
    fun verifyOkHttpFailure() {
        // ARRANGE
        // configure mock webserver to call onFailure
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val okHttpClientLatch = CountDownLatch(1)

        // ACT
        val typedRequest = DeviceCodeRequest("device-id")
        var apiResult: CaptureResult<DeviceCodeResponse>? = null
        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(HttpApiEndpoint.GetTemporaryDeviceCode, typedRequest) { result ->
            apiResult = result
            okHttpClientLatch.countDown()
        }

        // ASSERT
        assert(okHttpClientLatch.await(1, TimeUnit.SECONDS))
        assertThat(apiResult?.unwrapError() is ApiError.NetworkError).isTrue
    }

    @Test
    fun verifyServerHttpError() {
        // ARRANGE
        server.enqueue(MockResponse().setResponseCode(500))
        val okHttpClientLatch = CountDownLatch(1)

        // ACT
        val typedRequest = DeviceCodeRequest("device-id")
        var apiResult: CaptureResult<DeviceCodeResponse>? = null
        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(HttpApiEndpoint.GetTemporaryDeviceCode, typedRequest) { result ->
            apiResult = result
            okHttpClientLatch.countDown()
        }

        // ASSERT
        assert(okHttpClientLatch.await(1, TimeUnit.SECONDS))
        val serverError = apiResult?.unwrapError() as ApiError.ServerError
        assertThat(serverError.statusCode).isEqualTo(500)
    }

    @Test
    fun verifyJsonResponseError() {
        // ARRANGE
        server.enqueue(MockResponse().setBody("not a json"))
        val okHttpClientLatch = CountDownLatch(1)

        // ACT
        val typedRequest = DeviceCodeRequest("device-id")
        var apiResult: CaptureResult<DeviceCodeResponse>? = null
        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(HttpApiEndpoint.GetTemporaryDeviceCode, typedRequest) { result ->
            apiResult = result
            okHttpClientLatch.countDown()
        }

        // ASSERT
        assert(okHttpClientLatch.await(1, TimeUnit.SECONDS))
        assertThat(apiResult?.unwrapError() is ApiError.SerializationError).isTrue
    }
}
