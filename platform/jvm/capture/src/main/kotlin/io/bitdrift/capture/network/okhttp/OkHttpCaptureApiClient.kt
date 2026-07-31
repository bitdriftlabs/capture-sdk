// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.ApiError
import io.bitdrift.capture.CaptureResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal sealed class HttpApiEndpoint(
    val path: String,
) {
    object GetTemporaryDeviceCode : HttpApiEndpoint("v1/device/code")

    object ReportSdkError : HttpApiEndpoint("v1/sdk-errors")
}

/**
 * Small JSON-over-HTTP client for Capture's non-streaming backend APIs.
 *
 * This is used for secondary request/response endpoints such as temporary
 * device code creation and SDK error reporting.
 */
internal class OkHttpCaptureApiClient(
    private val apiBaseUrl: HttpUrl,
    private val apiKey: String,
    private val client: OkHttpClient,
) {
    private val jsonContentType: MediaType = "application/json".toMediaType()
    private val json =
        Json {
            explicitNulls = false
        }

    fun <Rq, Rp> perform(
        endpoint: HttpApiEndpoint,
        body: Rq,
        requestSerializer: KSerializer<Rq>,
        responseSerializer: KSerializer<Rp>,
        headers: Map<String, String>? = null,
        completion: (CaptureResult<Rp>) -> Unit,
    ) {
        execute(
            endpoint = endpoint,
            body = body,
            requestSerializer = requestSerializer,
            headers = headers,
            completion = completion,
        ) { responseBody ->
            try {
                CaptureResult.Success(json.decodeFromString(responseSerializer, responseBody))
            } catch (e: Exception) {
                CaptureResult.Failure(e.toSerializationError())
            }
        }
    }

    fun <Rq> performWithoutResponse(
        endpoint: HttpApiEndpoint,
        body: Rq,
        requestSerializer: KSerializer<Rq>,
        headers: Map<String, String>? = null,
        completion: (CaptureResult<Unit>) -> Unit,
    ) {
        execute(
            endpoint = endpoint,
            body = body,
            requestSerializer = requestSerializer,
            headers = headers,
            completion = completion,
        ) {
            CaptureResult.Success(Unit)
        }
    }

    private fun <Rq, Rp> execute(
        endpoint: HttpApiEndpoint,
        body: Rq,
        requestSerializer: KSerializer<Rq>,
        headers: Map<String, String>?,
        completion: (CaptureResult<Rp>) -> Unit,
        onSuccess: (String) -> CaptureResult<Rp>,
    ) {
        val jsonBody =
            try {
                json.encodeToString(requestSerializer, body)
            } catch (e: Exception) {
                completion(CaptureResult.Failure(e.toSerializationError()))
                return
            }

        val url = apiBaseUrl.newBuilder().addPathSegments(endpoint.path).build()
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .method("POST", jsonBody.toRequestBody(jsonContentType))
        headers?.let { requestBuilder.headers(it.toHeaders()) }
        requestBuilder.header("x-bitdrift-api-key", apiKey)

        client.newCall(requestBuilder.build()).enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.use {
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            completion(onSuccess(responseBody))
                        } else {
                            completion(CaptureResult.Failure(ApiError.ServerError(response.code, responseBody)))
                        }
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    completion(CaptureResult.Failure(e.toNetworkError()))
                }
            },
        )
    }

    private fun IOException.toNetworkError(): ApiError = ApiError.NetworkError(message = this.toString())

    private fun Exception.toSerializationError(): ApiError = ApiError.SerializationError(message = this.toString())
}
