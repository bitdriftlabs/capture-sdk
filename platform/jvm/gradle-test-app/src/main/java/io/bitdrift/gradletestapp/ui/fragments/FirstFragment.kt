// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.example.rocketreserver.BookTripsMutation
import com.example.rocketreserver.LaunchListQuery
import com.example.rocketreserver.LoginMutation
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.FeatureFlag
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.apollo.CaptureApolloInterceptor
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.retrofit.RetrofitUrlPathProvider
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import io.bitdrift.gradletestapp.data.service.BinaryJazzRetrofitService
import io.bitdrift.gradletestapp.diagnostics.fatalissues.FatalIssueGenerator
import io.bitdrift.gradletestapp.ui.compose.MainScreen
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModel
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.IOException
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Modern Fragment using Jetpack Compose following MVVM architecture
 * Replaces the old XML-based implementation with a clean Compose UI
 */
class FirstFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(SdkRepository(requireContext()))
    }

    private data class RequestDefinition(
        val method: String,
        val host: String,
        val path: String,
        val query: Map<String, String> = emptyMap(),
    )

    private var _composeView: ComposeView? = null

    private val requestDefinitions =
        listOf(
            RequestDefinition(method = "GET", host = "httpbin.org", path = "get"),
            RequestDefinition(method = "POST", host = "httpbin.org", path = "post"),
            RequestDefinition(method = "GET", host = "cat-fact.herokuapp.com", path = "facts/random"),
            RequestDefinition(method = "GET", host = "api.fisenko.net", path = "v1/quotes/en/random"),
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

    // This property is only valid between onCreateView and onDestroyView.
    private val composeView get() = _composeView!!
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var apolloClient: ApolloClient
    private lateinit var retrofitService: BinaryJazzRetrofitService
    private lateinit var sharedPreferences: SharedPreferences

    private var firstFragmentToCopySessionSpan: Span? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        firstFragmentToCopySessionSpan = Logger.startSpan("CreateFragmentToCopySessionClick", LogLevel.INFO)

        _composeView = ComposeView(requireContext())

        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    MainScreen(
                        onNavigateToConfig = {
                            Logger.logScreenView("config_fragment")
                            findNavController().navigate(R.id.action_FirstFragment_to_ConfigFragment)
                        },
                        onNavigateToWebView = {
                            Logger.logScreenView("web_view_fragment")
                            findNavController().navigate(R.id.action_FirstFragment_to_WebViewFragment)
                        },
                        onNavigateToCompose = {
                            Logger.logScreenView("compose_second_fragment")
                            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                        },
                        onNavigateToXml = {
                            Logger.logScreenView("xml_demo_fragment")
                            findNavController().navigate(R.id.action_FirstFragment_to_XmlFragment)
                        },
                        onPerformOkHttpRequest = { performOkHttpRequest() },
                        onPerformGraphQlRequest = { performGraphQlRequest() },
                        onPerformRetrofitRequest = { performRetrofitRequest() },
                        onAddOneFeatureFlag = { addOneFeatureFlag() },
                        onAddManyFeatureFlags = { addManyFeatureFlags() },
                        onClearFeatureFlags = { clearFeatureFlags() },
                        onForceAppExit = { reason -> forceAppExit(reason) },
                        viewModel = viewModel,
                    )
                }
            }
        }
        return composeView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)

        Logger.logScreenView("first_fragment")

        // Initialize network clients
        okHttpClient =
            OkHttpClient
                .Builder()
                .eventListenerFactory(
                    CaptureOkHttpEventListenerFactory(
                        extraFieldsProvider = RetrofitUrlPathProvider()
//                            OkHttpRequestFieldProvider { request ->
//                                mapOf(
//                                    "additional_network_request_field" to
//                                        request.url.host,
//                                )
//                            },
                    ),
                ).build()

        apolloClient =
            ApolloClient
                .Builder()
                .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
                .okHttpClient(okHttpClient)
                .addInterceptor(CaptureApolloInterceptor())
                .build()

        // https://binaryjazz.us/genrenator-api/
        val retrofitClient = Retrofit.Builder()
            .baseUrl("https://binaryjazz.us")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofitService = retrofitClient.create(BinaryJazzRetrofitService::class.java)

        firstFragmentToCopySessionSpan?.end(SpanResult.SUCCESS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _composeView = null
    }

    // Network operation methods - these are called from the Compose UI

    private fun performOkHttpRequest() {
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
                .method(requestDef.method, if (requestDef.method == "POST") "requestBody".toRequestBody() else null)
                .build()

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
                    Timber.v("OkHttp request completed with status code=${response.code} and body=$body")
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    Timber.v("OkHttp request failed with exception=$e")
                }
            },
        )
    }

    private val graphQlOperations by lazy {
        listOf(
            apolloClient.query(LaunchListQuery()),
            apolloClient.mutation(LoginMutation(email = "me@example.com")),
            apolloClient.mutation(BookTripsMutation(launchIds = listOf())),
        )
    }

    private fun performGraphQlRequest() {
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

    private fun performRetrofitRequest() {
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

    private fun addOneFeatureFlag() {
        Logger.setFeatureFlag("myflag", "myvariant")
    }

    private fun addManyFeatureFlags() {
        val flags = (1..10000).map { FeatureFlag.of("flag_" + it) }
        Logger.setFeatureFlags(flags)
    }

    private fun clearFeatureFlags() {
        Logger.clearFeatureFlags()
    }

    @SuppressLint("VisibleForTests")
    private fun forceAppExit(reason: AppExitReason) {
        when (reason) {
            AppExitReason.ANR_BLOCKING_GET -> FatalIssueGenerator.forceBlockingGetAnr()
            AppExitReason.ANR_BROADCAST_RECEIVER -> FatalIssueGenerator.forceBroadcastReceiverAnr(requireContext())
            AppExitReason.ANR_COROUTINES -> FatalIssueGenerator.forceCoroutinesAnr()
            AppExitReason.ANR_DEADLOCK -> FatalIssueGenerator.forceDeadlockAnr()
            AppExitReason.ANR_GENERIC -> FatalIssueGenerator.forceGenericAnr(requireContext())
            AppExitReason.ANR_SLEEP_MAIN_THREAD -> FatalIssueGenerator.forceThreadSleepAnr()
            AppExitReason.APP_CRASH_COROUTINE_EXCEPTION -> FatalIssueGenerator.forceCoroutinesCrash()
            AppExitReason.APP_CRASH_REGULAR_JVM_EXCEPTION -> FatalIssueGenerator.forceUnhandledException()
            AppExitReason.APP_CRASH_RX_JAVA_EXCEPTION -> FatalIssueGenerator.forceRxJavaException()
            AppExitReason.APP_CRASH_OUT_OF_MEMORY -> FatalIssueGenerator.forceOutOfMemoryCrash()
            AppExitReason.NATIVE_CAPTURE_DESTROY_CRASH -> FatalIssueGenerator.forceCaptureNativeCrash()
            AppExitReason.NATIVE_SIGSEGV -> FatalIssueGenerator.forceNativeSegmentationFault()
            AppExitReason.NATIVE_SIGBUS -> FatalIssueGenerator.forceNativeBusError()
            AppExitReason.SYSTEM_EXIT -> exitProcess(0)
        }
    }
}
