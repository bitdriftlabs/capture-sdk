// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.MaterialTheme
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Text
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.apollographql.apollo3.ApolloClient
import com.example.rocketreserver.LaunchListQuery
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.apollo3.CaptureApollo3Interceptor
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
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

    private val requestDefinitions = listOf(
        RequestDefinition(method = "GET", host = "httpbin.org", path = "get"),
        RequestDefinition(method = "POST", host = "httpbin.org", path = "post"),
        RequestDefinition(method = "GET", host = "cat-fact.herokuapp.com", path = "facts/random"),
        RequestDefinition(method = "GET", host = "api.fisenko.net", path = "v1/quotes/en/random"),
        RequestDefinition(method = "GET", host = "api.census.gov", path = "data/2021/pep/population", query = mapOf(
            "get" to "DENSITY_2021,NAME,STATE",
            "for" to "state:36"
        ))
    )

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var apolloClient: ApolloClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

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
                    Text("Hello from Compose!")
                }
            }
        }
        return viewRoot
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCopySessionUrl.setOnClickListener(this::copySessionUrl)
        binding.btnStartNewSession.setOnClickListener(this::startNewSession)
        binding.btnTempDeviceCode.setOnClickListener(this::getTempDeviceCode)
        binding.btnOkHttpRequest.setOnClickListener(this::performOkHttpRequest)
        binding.btnGraphQlRequest.setOnClickListener(this::performGraphQlRequest)
        binding.btnLogMessage.setOnClickListener(this::logMessage)
        binding.btnAppExit.setOnClickListener(this::forceAppExit)
        binding.btnNavigateCompose.setOnClickListener {
            Timber.i("Navigating to Compose Fragment")
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        binding.textviewFirst.text = Logger.sessionId

        val items = LogLevel.values().map { it.name }.toTypedArray()
        (binding.logLevelItems as? MaterialAutoCompleteTextView)?.setSimpleItems(items)
        binding.logLevelItems.setText(LogLevel.INFO.name, false)

        binding.spnAppExitOptions.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            AppExitReason.entries
        )

        okHttpClient = provideOkHttpClient()
        apolloClient = provideApolloClient()
    }

    private fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .eventListenerFactory(CaptureOkHttpEventListenerFactory())
            .build()
    }

    private fun provideApolloClient(): ApolloClient {
        return ApolloClient.Builder()
            .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
            .addInterceptor(CaptureApollo3Interceptor())
            .build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun copySessionUrl(view: View) {
        val data = ClipData.newPlainText("sessionUrl", Logger.sessionUrl)
        clipboardManager.setPrimaryClip(data)
    }

    private fun startNewSession(view: View) {
        Logger.startNewSession()
        binding.textviewFirst.text = Logger.sessionId
        Timber.i("Logger startNewSession with session url=${Logger.sessionUrl}")
    }

    private fun getTempDeviceCode(view: View) {
        Logger.trackSpan("createTemporaryDeviceCode", LogLevel.INFO) {
            Logger.createTemporaryDeviceCode(completion = { result ->
                result.onSuccess {
                    updateDeviceCodeValue(it)
                }
                result.onFailure {
                    updateDeviceCodeValue("$it")
                }
            })
        }
    }

    private fun updateDeviceCodeValue(deviceCode: String) {
        binding.deviceCodeTextView.text = deviceCode
        val data = ClipData.newPlainText("deviceCode", deviceCode)
        clipboardManager.setPrimaryClip(data)
    }

    private fun performOkHttpRequest(view: View) {
        val requestDef = requestDefinitions.random()
        Timber.i("Performing OkHttp Network Request: $requestDef")

        val url = HttpUrl.Builder().scheme("https").host(requestDef.host).addPathSegments(requestDef.path)
        requestDef.query.forEach { (key, value) -> url.addQueryParameter(key, value) }

        val request = Request.Builder()
            .url(url.build())
            .method(requestDef.method, if (requestDef.method == "POST") "requestBody".toRequestBody() else null)
            .build()

        val call = okHttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val s = response.body!!.string()
                Timber.v("Http request completed with status code=${response.code}")
            }

            override fun onFailure(call: Call, e: IOException) {
                Timber.v("Http request failed with exception=${e.javaClass::class.simpleName}")
            }
        })
    }

    private fun performGraphQlRequest(view: View) {
        MainScope().launch {
            val response = apolloClient.query(LaunchListQuery()).execute()
            Logger.logDebug(mapOf("response_data" to response.data.toString())) { "GraphQL response data received" }
        }

    }

    private fun logMessage(view: View?) {
        val logLevel = LogLevel.valueOf(binding.logLevelItems.text.toString())
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
            AppExitReason.APP_CRASH_EXCEPTION -> {
                throw RuntimeException("Forced unhandled exception")
            }
            AppExitReason.ANR -> {
                Thread.sleep(15000)
            }
            AppExitReason.SYSTEM_EXIT -> {
                exitProcess(0)
            }
            AppExitReason.APP_CRASH_NATIVE -> {
                val logger = Capture.logger()
                CaptureJniLibrary.destroyLogger((logger as LoggerImpl).loggerId)
                Logger.logInfo { "Forced native crash" }
            }
        }
    }

    enum class AppExitReason {
        APP_CRASH_EXCEPTION,
        APP_CRASH_NATIVE,
        SYSTEM_EXIT,
        ANR
    }
}
