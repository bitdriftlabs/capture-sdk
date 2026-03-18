// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import com.example.rocketreserver.BookTripsMutation
import com.example.rocketreserver.LaunchListQuery
import com.example.rocketreserver.LoginMutation
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.apollo.CaptureApolloInterceptor
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.okhttp.CaptureOkHttpTracingInterceptor
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import io.bitdrift.capture.network.okhttp.OkHttpResponseFieldProvider
import io.bitdrift.capture.network.retrofit.RetrofitUrlPathProvider
import io.bitdrift.gradletestapp.data.service.BinaryJazzRetrofitService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.random.Random

/**
 * Performs OkHttp/GraphQL requests
 */
class NetworkTestingRepository(context: Context) {

    private val chuckerInterceptor: ChuckerInterceptor =
        ChuckerInterceptor
            .Builder(context)
            .collector(
                ChuckerCollector(
                    context = context,
                    showNotification = true,
                    retentionPeriod = RetentionManager.Period.ONE_HOUR,
                ),
            ).alwaysReadResponseBody(true)
            .build()

    // Manual integration: explicit CaptureOkHttpEventListenerFactory with custom field providers
    private val okHttpClientManual: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(chuckerInterceptor)
            .addInterceptor(CaptureOkHttpTracingInterceptor())
            .eventListenerFactory(
                CaptureOkHttpEventListenerFactory(
                    requestFieldProvider = RetrofitUrlPathProvider(CustomRequestFieldProvider()),
                    responseFieldProvider = CustomResponseFieldProvider(),
                ),
            )
            .build()

    // Automatic integration: relies on Gradle plugin instrumentation (tests PROXY vs OVERWRITE)
    private val okHttpClientAutomatic: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(chuckerInterceptor)
            .eventListenerFactory { TimberOkHttpEventListener() }
            .build()

    private val apolloClient: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
            .okHttpClient(okHttpClientManual)
            .addInterceptor(CaptureApolloInterceptor())
            .build()
    private val retrofitService = Retrofit.Builder()
        .baseUrl("https://binaryjazz.us")
        .client(okHttpClientManual)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BinaryJazzRetrofitService::class.java)

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
        performOkHttpRequestWithClient(okHttpClientManual, "Manual")
    }

    fun performOkHttpRequestAutomatic() {
        performOkHttpRequestWithClient(okHttpClientAutomatic, "Automatic")
    }

    private fun performOkHttpRequestWithClient(okHttpClient: OkHttpClient, label: String) {
        val requestDef = requestDefinitions.random()
        Timber.i("Performing OkHttp Network Request ($label): $requestDef")

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
                    Timber.v("OkHttp request ($label) completed with status code=${response.code} and body=$body")
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    Timber.v("OkHttp request ($label) failed with exception=$e")
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

    fun performRetrofitRequest() {
        MainScope().launch {
            try {
                val count = (1..5).random()
                val response = if (Random.nextBoolean()) {
                    retrofitService.generateGenres(count)
                } else {
                    retrofitService.generateStories(count)
                }
                Timber.v("Retrofit request completed with status code=${response.code()} and body=${response.body()}")
            } catch (e: Exception) {
                Timber.e(e, "Retrofit request failed")
            }
        }
    }

    fun performPreExistingW3cRequest() {
        val traceId = generateFakeTraceId()
        val spanId = generateFakeSpanId()
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .header("traceparent", "00-$traceId-$spanId-01")
            .build()
        performRequestWithPreExistingHeaders(request, "Pre-existing W3C")
    }

    fun performPreExistingB3SingleRequest() {
        val traceId = generateFakeTraceId()
        val spanId = generateFakeSpanId()
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .header("b3", "$traceId-$spanId-1")
            .build()
        performRequestWithPreExistingHeaders(request, "Pre-existing B3 Single")
    }

    fun performPreExistingB3MultiRequest() {
        val traceId = generateFakeTraceId()
        val spanId = generateFakeSpanId()
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .header("X-B3-TraceId", traceId)
            .header("X-B3-SpanId", spanId)
            .header("X-B3-Sampled", "1")
            .build()
        performRequestWithPreExistingHeaders(request, "Pre-existing B3 Multi")
    }

    private fun performRequestWithPreExistingHeaders(request: Request, label: String) {
        Timber.i("Performing OkHttp request ($label): ${request.url}")
        okHttpClientManual.newCall(request).enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val body = response.use { it.body!!.string() }
                    Timber.v("OkHttp request ($label) completed with status code=${response.code} and body=$body")
                }

                override fun onFailure(call: Call, e: IOException) {
                    Timber.v("OkHttp request ($label) failed with exception=$e")
                }
            },
        )
    }

    private fun generateFakeTraceId(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateFakeSpanId(): String {
        val bytes = ByteArray(8)
        Random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Logs OkHttp events via Timber. Used with auto-instrumentation to test PROXY vs OVERWRITE.
     */
    private class TimberOkHttpEventListener : EventListener() {
        override fun callStart(call: Call) {
            Timber.d("[TimberOkHttpEventListener] callStart: ${call.request().url}")
        }

        override fun callEnd(call: Call) {
            Timber.d("[TimberOkHttpEventListener] callEnd: ${call.request().url}")
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            Timber.d("[TimberOkHttpEventListener] connectStart")
        }

        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?
        ) {
            Timber.d("[TimberOkHttpEventListener] connectEnd")
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            Timber.d("[TimberOkHttpEventListener] connectionAcquired")
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