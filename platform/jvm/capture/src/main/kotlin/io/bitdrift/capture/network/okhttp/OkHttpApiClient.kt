// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import android.util.Log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.bitdrift.capture.ApiError
import io.bitdrift.capture.CaptureResult
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

internal class OkHttpApiClient(
    private val apiBaseUrl: HttpUrl,
    private val apiKey: String,
) {
    private val client: OkHttpClient = OkHttpClient()
    private val jsonContentType: MediaType = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    inline fun <reified Rq, reified Rp> perform(
        endpoint: HttpApiEndpoint,
        body: Rq,
        headers: Map<String, String>? = null,
        noinline completion: (CaptureResult<Rp>) -> Unit,
    ) {
        val jsonBody =
            try {
                json.encodeToString(body)
            } catch (e: Exception) {
                completion(Err(e.toSerializationError()))
                return
            }
        val url = apiBaseUrl.newBuilder().addPathSegments(endpoint.path).build()
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .method("POST", jsonBody.toRequestBody(jsonContentType))
        headers?.let {
            requestBuilder.headers(it.toHeaders())
        }
        requestBuilder.header("x-bitdrift-api-key", apiKey)
        client.newCall(requestBuilder.build()).enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.use {
                        if (response.isSuccessful) {
                            try {
                                val typedResponse = json.decodeFromString<Rp>(response.body?.string().orEmpty())
                                completion(Ok(typedResponse))
                            } catch (e: Exception) {
                                completion(Err(e.toSerializationError()))
                            }
                        } else {
                            val responseBody = response.body?.string()
                            completion(Err(ApiError.ServerError(response.code, responseBody)))
                        }
                        Log.e("bitdrift", "done")
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    completion(Err(e.toNetworkError()))
                }
            },
        )
    }

    private fun IOException.toNetworkError(): ApiError = ApiError.NetworkError(message = this.toString())

    private fun Exception.toSerializationError(): ApiError = ApiError.SerializationError(message = this.toString())
}
