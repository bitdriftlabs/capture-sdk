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
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcException
import com.squareup.wire.ProtoAdapter
import grpcbin.DummyMessage
import grpcbin.GRPCBinClient
import grpcbin.SpecificErrorRequest
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.ClientCalls
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.apollo.CaptureApolloInterceptor
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.okhttp.CaptureOkHttpTracingInterceptor
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import io.bitdrift.capture.network.okhttp.OkHttpResponseFieldProvider
import io.bitdrift.capture.network.retrofit.RetrofitUrlPathProvider
import io.bitdrift.gradletestapp.BuildConfig
import io.bitdrift.gradletestapp.data.service.BinaryJazzRetrofitService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
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

    private val initialOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(chuckerInterceptor)
            .applyCaptureInstrumentation()
            .build()

    // Exercises automatic instrumentation when libraries derive clients from an existing client.
    private val okHttpClient: OkHttpClient =
        initialOkHttpClient
            .newBuilder()
            .build()
    private val apolloClient: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
            .okHttpClient(okHttpClient)
            .addInterceptor(CaptureApolloInterceptor())
            .build()
    private val retrofitService = Retrofit.Builder()
        .baseUrl("https://binaryjazz.us")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BinaryJazzRetrofitService::class.java)

    private val wireGrpcClient: GRPCBinClient =
        GrpcClient
            .Builder()
            .client(
                OkHttpClient
                    .Builder()
                    .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    .applyCaptureInstrumentation()
                    .build(),
            ).baseUrl(GRPCBIN_BASE_URL)
            .minMessageToCompress(Long.MAX_VALUE)
            .build()
            .create(GRPCBinClient::class)

    private val grpcJavaChannel: ManagedChannel by lazy {
        OkHttpChannelBuilder
            .forAddress(GRPCBIN_HOST, GRPCBIN_PORT)
            .useTransportSecurity()
            .build()
    }

    private val grpcJavaDummyUnaryMethod: MethodDescriptor<DummyMessage, DummyMessage> =
        MethodDescriptor
            .newBuilder<DummyMessage, DummyMessage>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("grpcbin.GRPCBin", "DummyUnary"))
            .setRequestMarshaller(WireMarshaller(DummyMessage.ADAPTER))
            .setResponseMarshaller(WireMarshaller(DummyMessage.ADAPTER))
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
        val label =
            if (BuildConfig.ENABLE_AUTO_CAPTURE_OKHTTP_INSTRUMENTATION) {
                "autoOkHttpInstrumentation:${BuildConfig.AUTO_CAPTURE_OKHTTP_INSTRUMENTATION_TYPE}"
            } else {
                "manualOkHttpInstrumentation"
            }
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
                            it.body.string()
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

    /**
     * Requests httpbin's /delay endpoint, which holds the response for the given number of
     * seconds before returning — useful for testing behavior around slow/late-arriving requests,
     * e.g. ones started while the SDK is still starting.
     */
    fun performDelayedOkHttpRequest() {
        val request =
            Request
                .Builder()
                .url("https://httpbin.org/delay/3")
                .build()

        Timber.i("Performing delayed (3s) OkHttp request: ${request.url}")
        performRequestWithPreExistingHeaders(request, "Delayed 3s")
    }

    fun performOkHttpFailureBeforeResponseHeaders() {
        val request =
            Request
                .Builder()
                .url("https://nonexistent.invalid/")
                .build()

        Timber.i("Performing OkHttp request expected to fail before response headers: ${request.url}")
        performRequestWithPreExistingHeaders(request, "Pre-response failure")
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

    fun performPreExistingDatadogRequest() {
        val traceId = generateFakeDatadogTraceId()
        val spanId = generateFakeDatadogSpanId()
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .header("x-datadog-trace-id", traceId)
            .header("x-datadog-parent-id", spanId)
            .header("x-datadog-sampling-priority", "2")
            .build()
        performRequestWithPreExistingHeaders(request, "Pre-existing DD")
    }

    fun performLocalBackendAddToCartRequest() {
        val requestBody = JSONObject()
            .put("product_id", LOCAL_BACKEND_PRODUCT_ID)
            .put("quantity", 1)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$LOCAL_BACKEND_BASE_URL/cart")
            .post(requestBody)
            .build()
        performRequestWithPreExistingHeaders(request, "Local Backend Add to Cart")
    }

    fun performLocalBackendGetCartRequest() {
        val request = Request.Builder()
            .url("$LOCAL_BACKEND_BASE_URL/cart")
            .get()
            .build()
        performRequestWithPreExistingHeaders(request, "Local Backend Get Cart")
    }

    fun performLocalBackendDeleteCartItemRequest() {
        val request = Request.Builder()
            .url("$LOCAL_BACKEND_BASE_URL/cart/$LOCAL_BACKEND_PRODUCT_ID")
            .delete()
            .build()
        performRequestWithPreExistingHeaders(request, "Local Backend Delete Cart Item")
    }

    fun performWireGrpcUnaryRequest() {
        MainScope().launch(Dispatchers.IO) {
            try {
                val response = wireGrpcClient.DummyUnary().execute(DummyMessage(f_string = "bitdrift"))
                Timber.v("Wire gRPC unary completed with response=$response")
            } catch (e: Exception) {
                Timber.e(e, "Wire gRPC unary failed")
            }
        }
    }

    fun performWireGrpcErrorRequest() {
        MainScope().launch(Dispatchers.IO) {
            try {
                wireGrpcClient
                    .SpecificError()
                    .execute(SpecificErrorRequest(code = GRPC_STATUS_NOT_FOUND, reason = "bitdrift"))
                Timber.v("Wire gRPC error call unexpectedly succeeded")
            } catch (e: GrpcException) {
                Timber.v("Wire gRPC error call completed with grpc-status=${e.grpcStatus.code} message=${e.grpcMessage}")
            } catch (e: Exception) {
                Timber.e(e, "Wire gRPC error call failed")
            }
        }
    }

    fun performWireGrpcServerStreamRequest() {
        MainScope().launch(Dispatchers.IO) {
            try {
                val (requests, responses) =
                    wireGrpcClient.DummyServerStream().executeIn(this)
                requests.send(DummyMessage(f_string = "bitdrift"))
                requests.close()
                var count = 0
                for (response in responses) {
                    count++
                }
                Timber.v("Wire gRPC server stream completed with $count messages")
            } catch (e: Exception) {
                Timber.e(e, "Wire gRPC server stream failed")
            }
        }
    }

    fun performGrpcJavaUnaryRequest() {
        MainScope().launch(Dispatchers.IO) {
            try {
                val response =
                    ClientCalls.blockingUnaryCall(
                        grpcJavaChannel,
                        grpcJavaDummyUnaryMethod,
                        CallOptions.DEFAULT,
                        DummyMessage(f_string = "bitdrift"),
                    )
                Timber.v("grpc-java unary completed with response=$response")
            } catch (e: StatusRuntimeException) {
                Timber.e(e, "grpc-java unary failed with status=${e.status}")
            }
        }
    }

    private fun performRequestWithPreExistingHeaders(request: Request, label: String) {
        Timber.i("Performing OkHttp request ($label): ${request.url}")
        okHttpClient.newCall(request).enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val body = response.use { it.body.string() }
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

    private fun generateFakeDatadogTraceId(): String {
        var value = Random.nextLong().toULong()
        while (value == 0UL) {
            value = Random.nextLong().toULong()
        }
        return value.toString()
    }

    private fun generateFakeDatadogSpanId(): String {
        var value = Random.nextLong().toULong()
        while (value == 0UL) {
            value = Random.nextLong().toULong()
        }
        return value.toString()
    }

    private fun OkHttpClient.Builder.applyCaptureInstrumentation(): OkHttpClient.Builder =
        apply {
            if (BuildConfig.ENABLE_AUTO_CAPTURE_OKHTTP_INSTRUMENTATION) {
                // The Gradle plugin installs Capture's listener and tracing interceptor.
                eventListenerFactory { TimberOkHttpEventListener() }
            } else {
                // Manual instrumentation is used when automatic instrumentation is disabled.
                addInterceptor(CaptureOkHttpTracingInterceptor())
                eventListenerFactory(
                    CaptureOkHttpEventListenerFactory(
                        requestFieldProvider = RetrofitUrlPathProvider(
                            CustomRequestFieldProvider()
                        ),
                        responseFieldProvider = CustomResponseFieldProvider(),
                    ),
                )
            }
        }

    private class WireMarshaller<T : Any>(
        private val adapter: ProtoAdapter<T>,
    ) : MethodDescriptor.Marshaller<T> {
        override fun stream(value: T): InputStream = ByteArrayInputStream(adapter.encode(value))

        override fun parse(stream: InputStream): T = adapter.decode(stream)
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
        override fun provideExtraFields(request: Request): Map<String, String> {
            if (request.isGrpc()) {
                Timber.i("[CaptureGrpc] request method=${request.method} url=${request.url}")
            }
            return mapOf("additional_network_request_host_field" to request.url.host)
        }
    }

    private class CustomResponseFieldProvider : OkHttpResponseFieldProvider {
        override fun provideExtraFields(response: Response): Map<String, String> {
            val fields =
                if (response.code >= 400) {
                    mapOf("additional_network_response_error_code_field" to response.code.toString())
                } else {
                    emptyMap()
                }
            if (!response.request.isGrpc()) {
                return fields
            }
            val grpcFields = response.grpcStatusFields()
            Timber.i(
                "[CaptureGrpc] response path=${response.request.url.encodedPath} " +
                    "http_status=${response.code} protocol=${response.protocol} grpc=$grpcFields",
            )
            return fields + grpcFields
        }

        private fun Response.grpcStatusFields(): Map<String, String> {
            val trailers =
                runCatching { trailers() }
                    .onFailure { Timber.i("[CaptureGrpc] trailers unavailable: $it") }
                    .getOrNull()
            Timber.i("[CaptureGrpc] headers=${headers.toMultimap()} trailers=${trailers?.toMultimap()}")
            val source = header("grpc-status")?.let { headers } ?: trailers
            val status = source?.get("grpc-status") ?: return emptyMap()
            return buildMap {
                put("grpc_status", status)
                source["grpc-message"]?.let { put("grpc_message", it) }
            }
        }
    }

    private companion object {
        private fun Request.isGrpc(): Boolean = header("content-type")?.startsWith("application/grpc") == true

        private const val LOCAL_BACKEND_BASE_URL = "http://10.0.2.2:5173/api"
        private const val LOCAL_BACKEND_PRODUCT_ID = "classic-tee"
        private const val GRPCBIN_HOST = "grpcb.in"
        private const val GRPCBIN_PORT = 9001
        private const val GRPCBIN_BASE_URL = "https://$GRPCBIN_HOST:$GRPCBIN_PORT"
        private const val GRPC_STATUS_NOT_FOUND = 5
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
