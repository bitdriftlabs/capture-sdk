// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.preference.PreferenceManager
import io.bitdrift.gradletestapp.ConfigurationSettingsFragment.Companion.DEFERRED_START_PREFS_KEY
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.MaterialTheme
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Text
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.example.rocketreserver.BookTripsMutation
import com.example.rocketreserver.LaunchListQuery
import com.example.rocketreserver.LoginMutation
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Error
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.apollo.CaptureApolloInterceptor
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import io.bitdrift.gradletestapp.databinding.FragmentFirstBinding
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
import kotlin.system.exitProcess

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private data class RequestDefinition(
        val method: String,
        val host: String,
        val path: String,
        val query: Map<String, String> = emptyMap(),
    )

    private var _binding: FragmentFirstBinding? = null

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
    private val binding get() = _binding!!
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var apolloClient: ApolloClient
    private lateinit var sharedPreferences: SharedPreferences

    private var firstFragmentToCopySessionSpan: Span? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        firstFragmentToCopySessionSpan = Logger.startSpan("CreateFragmentToCopySessionClick", LogLevel.INFO)

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        val viewRoot = binding.root

        clipboardManager = viewRoot.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        binding.composeView.apply {
            // Dispose of the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // In Compose world
                MaterialTheme {
                    Text(
                        text = "Text in Compose",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.secondary,
                    )
                }
            }
        }
        return viewRoot
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)

        Logger.logScreenView("first_fragment")
        binding.btnNavigateConfiguration.setOnClickListener {
            Logger.logScreenView("config_fragment")
            findNavController().navigate(R.id.action_FirstFragment_to_ConfigFragment)
        }
        binding.btnCopySessionUrl.setOnClickListener(this::copySessionUrl)
        binding.btnStartNewSession.setOnClickListener(this::startNewSession)
        binding.btnTempDeviceCode.setOnClickListener(this::getTempDeviceCode)
        binding.btnOkHttpRequest.setOnClickListener(this::performOkHttpRequest)
        binding.btnGraphQlRequest.setOnClickListener(this::performGraphQlRequest)
        binding.btnLogMessage.setOnClickListener(this::logMessage)
        binding.btnAppExit.setOnClickListener(this::forceAppExit)
        binding.btnNavigateToWebView.setOnClickListener {
            Logger.logScreenView("web_view_fragment")
            findNavController().navigate(R.id.action_FirstFragment_to_WebViewFragment)
        }
        binding.btnNavigateCompose.setOnClickListener {
            Timber.i("Navigating to Compose Fragment")
            Logger.logScreenView("compose_second_fragment")
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        setSpinnerAdapter(binding.spnAppExitOptions, AppExitReason.entries)
        setSpinnerAdapter(binding.logLevelItems, LogLevel.entries.map { it.name }.toList())

        if (sharedPreferences.getBoolean(DEFERRED_START_PREFS_KEY, false)) {
            binding.btnStartSdk.setOnClickListener(this::startSdkManually)
            binding.btnStartSdk.visibility = View.VISIBLE
            binding.textviewSdkStatus.visibility = View.VISIBLE
        } else {
            binding.textviewFirst.text = Logger.sessionId
        }

        okHttpClient =
            OkHttpClient
                .Builder()
                .eventListenerFactory(
                    CaptureOkHttpEventListenerFactory(
                        extraFieldsProvider =
                            OkHttpRequestFieldProvider { request ->
                                mapOf(
                                    "additional_network_request_field" to
                                        request.url.host,
                                )
                            },
                    ),
                ).build()

        apolloClient =
            ApolloClient
                .Builder()
                .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
                .okHttpClient(okHttpClient)
                .addInterceptor(CaptureApolloInterceptor())
                .build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun copySessionUrl(view: View) {
        val data = ClipData.newPlainText("sessionUrl", Logger.sessionUrl)
        clipboardManager.setPrimaryClip(data)
        firstFragmentToCopySessionSpan?.end(SpanResult.SUCCESS)
    }

    private fun startNewSession(view: View) {
        Logger.startNewSession()
        binding.textviewFirst.text = Logger.sessionId
        Timber.i("Logger startNewSession with session url=${Logger.sessionUrl}")
    }

    private fun getTempDeviceCode(view: View) {
        Logger.trackSpan("createTemporaryDeviceCode", LogLevel.INFO) {
            Logger.createTemporaryDeviceCode { result ->
                when (result) {
                    is CaptureResult.Success -> updateDeviceCodeValue(result.value)
                    is CaptureResult.Failure -> displayDeviceCodeError(result.error)
                }
            }
        }
    }

    private fun updateDeviceCodeValue(deviceCode: String) {
        binding.deviceCodeTextView.text = deviceCode
        val data = ClipData.newPlainText("deviceCode", deviceCode)
        clipboardManager.setPrimaryClip(data)
    }

    private fun displayDeviceCodeError(error: Error) {
        binding.deviceCodeTextView.text = error.message
    }

    private fun performOkHttpRequest(view: View) {
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

    private val graphQlOperations by lazy {
        listOf(
            apolloClient.query(LaunchListQuery()),
            apolloClient.mutation(LoginMutation(email = "me@example.com")),
            apolloClient.mutation(BookTripsMutation(launchIds = listOf())),
        )
    }

    private fun performGraphQlRequest(view: View) {
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

    private fun logMessage(view: View?) {
        val logLevel = LogLevel.valueOf(binding.logLevelItems.selectedItem.toString())
        val tag = "FirstFragment"
        val exception = Exception("custom exception")
        when (logLevel) {
            LogLevel.TRACE -> Timber.tag(tag).v("'log message' with level=%s", logLevel)
            LogLevel.DEBUG -> Timber.tag(tag).d("'log message' with level=%s", logLevel)
            LogLevel.INFO -> Timber.tag(tag).i("'log message' with level=%s", logLevel)
            LogLevel.WARNING -> Timber.tag(tag).w(exception, "'log message' with level=%s", logLevel)
            LogLevel.ERROR -> Timber.tag(tag).e(exception, "'log message' with level=%s", logLevel)
        }
        Toast.makeText(view?.context, "'log message' with level=$logLevel", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("VisibleForTests")
    private fun forceAppExit(view: View) {
        val selectedAppExitReason = binding.spnAppExitOptions.selectedItem.toString()
        when (AppExitReason.valueOf(selectedAppExitReason)) {
            AppExitReason.ANR_BLOCKING_GET -> FatalIssueGenerator.forceBlockingGetAnr()
            AppExitReason.ANR_BROADCAST_RECEIVER -> FatalIssueGenerator.forceBroadcastReceiverAnr(view.context)
            AppExitReason.ANR_COROUTINES -> FatalIssueGenerator.forceCoroutinesAnr()
            AppExitReason.ANR_DEADLOCK -> FatalIssueGenerator.forceDeadlockAnr()
            AppExitReason.ANR_GENERIC -> FatalIssueGenerator.forceGenericAnr(view.context)
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

    private fun setSpinnerAdapter(
        spinner: Spinner,
        items: List<*>,
    ) {
        spinner.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                items,
            )
    }

    private fun startSdkManually(view: View) {
        BitdriftInit.initFromPreferences(sharedPreferences, view.context)
        binding.btnStartSdk.visibility = View.GONE
        binding.textviewSdkStatus.visibility = View.GONE
        binding.textviewFirst.text = Logger.sessionId
    }

    enum class AppExitReason {
        ANR_BLOCKING_GET,
        ANR_BROADCAST_RECEIVER,
        ANR_COROUTINES,
        ANR_DEADLOCK,
        ANR_GENERIC,
        ANR_SLEEP_MAIN_THREAD,
        APP_CRASH_COROUTINE_EXCEPTION,
        APP_CRASH_REGULAR_JVM_EXCEPTION,
        APP_CRASH_RX_JAVA_EXCEPTION,
        APP_CRASH_OUT_OF_MEMORY,

        NATIVE_CAPTURE_DESTROY_CRASH,

        NATIVE_SIGSEGV,

        NATIVE_SIGBUS,

        SYSTEM_EXIT,
    }
}
