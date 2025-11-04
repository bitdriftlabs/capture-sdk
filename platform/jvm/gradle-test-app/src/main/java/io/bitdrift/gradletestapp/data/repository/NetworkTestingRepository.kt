// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.example.rocketreserver.BookTripsMutation
import com.example.rocketreserver.LaunchListQuery
import com.example.rocketreserver.LoginMutation
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.apollo.CaptureApolloInterceptor
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import io.bitdrift.capture.network.okhttp.OkHttpResponseFieldProvider
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * Performs OkHttp/GraphQL requests
 */
class NetworkTestingRepository {
    // Initialize network clients
    private val okHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .eventListenerFactory(
                CaptureOkHttpEventListenerFactory(
                    requestFieldProvider = CustomRequestFieldProvider(),
                    responseFieldProvider = CustomResponseFieldProvider(),
                ),
            ).build()
    private val apolloClient: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
            .okHttpClient(okHttpClient)
            .addInterceptor(CaptureApolloInterceptor())
            .build()

    private data class RequestDefinition(
        val method: String,
        val host: String,
        val path: String,
        val query: Map<String, String> = emptyMap(),
    )

    private val requestDefinitions =
        listOf(
            RequestDefinition(method = "GET", host = "httpbin.org", path = "get"),
            RequestDefinition(method = "POST", host = "httpbin.org", path = "post"),
            RequestDefinition(
                method = "GET",
                host = "cat-fact.herokuapp.com",
                path = "facts/random",
            ),
            RequestDefinition(
                method = "GET",
                host = "api.fisenko.net",
                path = "v1/quotes/en/random",
            ),
            RequestDefinition(
                method = "GET",
                host = "api.census.gov",
                path = "data/2021/pep/population",
                query =
                    mapOf(
                        "get" to "DENSITY_2021,NAME,STATE",
                        "for" to "state:36",
                    ),
            ),
        )

    private val graphQlOperations by lazy {
        listOf(
            apolloClient.query(LaunchListQuery()),
            apolloClient.mutation(LoginMutation(email = "me@example.com")),
            apolloClient.mutation(BookTripsMutation(launchIds = listOf())),
        )
    }

    fun performOkHttpRequest() {
        val requestDef = requestDefinitions.random()
        Timber.i("Performing OkHttp Network Request: $requestDef")

        val url =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(requestDef.host)
                .addPathSegments(requestDef.path)
        requestDef.query.forEach { (key, value) -> url.addQueryParameter(key, value) }

        val request =
            Request
                .Builder()
                .url(url.build())
                .method(
                    requestDef.method,
                    if (requestDef.method == "POST") "requestBody".toRequestBody() else null,
                ).build()

        val call = okHttpClient.newCall(request)

        call.enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    val body =
                        response.use {
                            it.body!!.string()
                        }
                    Timber.v("Http request completed with status code=${response.code} and body=$body")
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    Timber.v("Http request failed with exception=$e")
                }
            },
        )
    }

    fun performGraphQlRequest() {
        val operation = graphQlOperations.random()
        MainScope().launch {
            try {
                val response = operation.execute()
                Logger.logDebug(mapOf("response_data" to response.data.toString())) { "GraphQL response data received" }
            } catch (e: Exception) {
                Timber.e(e, "GraphQL request failed")
            }
        }
    }

    private class CustomRequestFieldProvider : OkHttpRequestFieldProvider {
        override fun provideExtraFields(request: Request): Map<String, String> =
            mapOf("additional_network_request_host_field" to request.url.host)
    }

    private class CustomResponseFieldProvider : OkHttpResponseFieldProvider {
        override fun provideExtraFields(response: Response): Map<String, String> =
            if (response.code >= 400) {
                mapOf("additional_network_response_error_code_field" to response.code.toString())
            } else {
                emptyMap()
            }
    }
}
