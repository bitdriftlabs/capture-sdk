// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.helloworld

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.FilteredCapture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MainActivity : ComponentActivity() {

    private val replayPreviewClient: ReplayPreviewClient by lazy {
        ReplayPreviewClient(ReplayModule(
            object: ErrorHandler {
                override fun handleError(detail: String, e: Throwable?) {
                    Log.e("HelloWorldApp", "Replay handleError: $detail $e")
                }
            },
            object: ReplayLogger {
                override fun onScreenCaptured(
                    encodedScreen: ByteArray,
                    screen: FilteredCapture,
                    metrics: EncodedScreenMetrics
                ) {
                    Log.i("HelloWorldApp", "Replay onScreenCaptured: took=${metrics.captureTimeMs}ms")
                    Log.i("HelloWorldApp", "Replay onScreenCaptured: screen=${screen}")
                    Log.i("HelloWorldApp", "Replay onScreenCaptured: encodedScreen=${Base64.encodeToString(encodedScreen, 0)}")
                }

                override fun logVerboseInternal(message: String, fields: Map<String, String>?) {
                    Log.v("HelloWorldApp", message)
                }

                override fun logDebugInternal(message: String, fields: Map<String, String>?) {
                    Log.d("HelloWorldApp", message)
                }

                override fun logErrorInternal(message: String, e: Throwable?, fields: Map<String, String>?) {
                    Log.e("HelloWorldApp", message, e)
                }
            },
            SessionReplayConfiguration(),
            this.applicationContext
        ), this.applicationContext)
    }
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var client: OkHttpClient
    private lateinit var sessionIdTextView: TextView
    private lateinit var deviceCodeTextView: TextView
    private lateinit var logLevelSpinner: Spinner
    private lateinit var appExitReasonSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.android_main)
        Log.v("HelloWorldApp", "MainActivity launched")

        clipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        sessionIdTextView = findViewById(R.id.session_id)
        sessionIdTextView.text = Logger.sessionId

        deviceCodeTextView = findViewById(R.id.device_code)

        logLevelSpinner = findViewById(R.id.spinner)
        logLevelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LogLevel.values())
        appExitReasonSpinner = findViewById(R.id.spinner2)
        appExitReasonSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, AppExitReason.values())

        createComposeUI()

        client = OkHttpClient.Builder()
            .eventListenerFactory(CaptureOkHttpEventListenerFactory())
            .build()

        Logger.addField("field_container_field_key", "field_container_field_value")
        Logger.logInfo(mapOf("key" to "value")) { "MainActivity onCreate called" }

        Logger.logAppLaunchTTI(1.toDuration(DurationUnit.SECONDS))
    }

    private fun createComposeUI() {
        // We have a container in our Activity layout so lets grab that so we can add our ComposeView.
        val container = findViewById<ViewGroup>(R.id.compose_container)

        // We create our new ComposeView to match the height and width of the container.
        val composeView = ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            // The setContent call takes a Composable lambda extension which can render Composable UI.
            setContent {
                // We then render a simple Text component from Compose.
                Text(
                    text = "Hello I am from Compose",
                    modifier = Modifier.fillMaxSize().testTag("my-custom-test-compose-view")
                )
            }
        }

        // Finally we add the ComposeView to the container to be rendered.
        container.addView(composeView)
    }

    fun runLogger(@Suppress("UNUSED_PARAMETER") view: View) {
        val selectedLogLevel = logLevelSpinner.selectedItem.toString()
        Log.v("HelloWorldApp", "Calling Log method with level=$selectedLogLevel")
        val myException = Exception("Fake Test Exception")
        Logger.log(LogLevel.valueOf(selectedLogLevel), mapOf("key" to "value"), myException) { "$selectedLogLevel log sent from Hello World!!" }
    }

    fun makeOkHttpRequest(@Suppress("UNUSED_PARAMETER") view: View) {
        val req = Request.Builder()
            .url(HttpUrl.Builder().scheme("https").host("api-fe.bitdrift.io").addPathSegment("/fe/ping").build())
            .method("GET", null)
            .build()

        val call = client.newCall(req)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.v("HelloWorldApp", "Http request completed with status code=${response.code}")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.v("HelloWorldApp", "Http request failed with exception=${e.javaClass::class.simpleName}")
            }
        })
    }

    fun copySessionUrl(@Suppress("UNUSED_PARAMETER") view: View) {
        val data = ClipData.newPlainText("sessionUrl", Logger.sessionUrl)
        clipboardManager.setPrimaryClip(data)
    }

    fun startNewSession(@Suppress("UNUSED_PARAMETER") view: View) {
        Logger.startNewSession()
        sessionIdTextView.text = Logger.sessionId
    }

    fun generateTmpDeviceCode(view: View) {
        Logger.createTemporaryDeviceCode(completion = { result ->
            result.onSuccess {
                updateDeviceCodeValue(it)
            }
            result.onFailure {
                updateDeviceCodeValue(it.message)
            }
        })
    }

    private fun updateDeviceCodeValue(deviceCode: String) {
        deviceCodeTextView.text = deviceCode
        val data = ClipData.newPlainText("deviceCode", deviceCode)
        clipboardManager.setPrimaryClip(data)
    }


    fun startReplayStream(@Suppress("UNUSED_PARAMETER") view: View) {
        replayPreviewClient.connect()
        replayPreviewClient.captureScreen()
        findViewById<Button>(R.id.cop_replay_base64_btn).isEnabled = true
    }

    fun copyReplayBase64(@Suppress("UNUSED_PARAMETER") view: View) {
        val data = ClipData.newPlainText("replayBase64", replayPreviewClient.getLastCapturedScreen())
        clipboardManager.setPrimaryClip(data)
    }

    fun forceAppExit(@Suppress("UNUSED_PARAMETER") view: View) {
        val selectedAppExitReason = appExitReasonSpinner.selectedItem.toString()
        when (AppExitReason.valueOf(selectedAppExitReason)) {
            AppExitReason.APP_CRASH_EXCEPTION_MAIN -> {
                throw RuntimeException("Forced unhandled exception in main thread")
            }
            AppExitReason.APP_CRASH_EXCEPTION_BG -> {
                Thread {
                    throw RuntimeException("Forced unhandled exception in background thread")
                }.start()
            }
            AppExitReason.ANR -> {
                Thread.sleep(6000)
            }
            AppExitReason.SYSTEM_EXIT -> {
                exitProcess(0)
            }
        }
    }

    enum class AppExitReason {
        APP_CRASH_EXCEPTION_MAIN,
        APP_CRASH_EXCEPTION_BG,
        SYSTEM_EXIT,
        ANR
    }
}
