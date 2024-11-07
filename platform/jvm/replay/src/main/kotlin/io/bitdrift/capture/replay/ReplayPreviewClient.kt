// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.util.Base64
import android.util.Log
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.FilteredCapture
import io.bitdrift.capture.replay.internal.ReplayCapture
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Allows to capture the screen and send the binary data over a persistent websocket connection
 * @param replayModule The replay module to use for screen capture
 * @param context The context of the app to capture
 * @param protocol The protocol to use for the websocket connection (default is ws)
 * @param host The host to connect to (default is Android's emulator loopback IP: 10.0.2.2)
 * @param port The port to connect to (default is 3001)
 */
class ReplayPreviewClient(
    private val replayModule: ReplayModule, // TODO(murki): [Replay] Refactor to not expose this
    protocol: String = "ws",
    host: String = "10.0.2.2",
    port: Int = 3001,
) : ReplayLogger {

    private val replayCapture: ReplayCapture = ReplayCapture(replayModule.sessionReplayConfiguration, replayModule.errorHandler, replayModule.displayManager)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "io.bitdrift.capture.session-replay-client")
    }
    // Calling this is necessary to capture the display size
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val request: Request = Request.Builder()
        .url("$protocol://$host:$port")
        .build()
    private var webSocket: WebSocket? = null
    private var lastEncodedScreen: ByteArray? = null

    /**
     * Creates a socket and establishes a connection. Terminates any previous connection
     */
    fun connect() {
        webSocket?.close(1001, "ReplayPreviewClient closing and re-creating socket")
        webSocket = client.newWebSocket(
            request,
            WebSocketLogger,
        )
        Log.d("ReplayPreviewClient", "New Web Socket created")
    }

    /**
     * Capture the screen and send it over the websocket connection after processing
     */
    fun captureScreen() {
        replayCapture.captureScreen(executor, skipReplayComposeViews = false) { encodedScreen, screen, metrics ->
            replayModule.logger.onScreenCaptured(encodedScreen, screen, metrics)
        }
    }

    /**
     * Get the last captured screen. Needs [captureScreen] to be called at least once
     * @return The last captured screen as a base64 encoded string
     */
    fun getLastCapturedScreen(): String {
        return if (lastEncodedScreen != null) {
            String(Base64.encode(lastEncodedScreen, Base64.DEFAULT))
        } else {
            ""
        }
    }

    override fun onScreenCaptured(encodedScreen: ByteArray, screen: FilteredCapture, metrics: EncodedScreenMetrics) {
        lastEncodedScreen = encodedScreen
        webSocket?.send(encodedScreen.toByteString(0, encodedScreen.size))
        // forward the callback to the module's logger
        replayModule.logger.onScreenCaptured(encodedScreen, screen, metrics)
    }

    override fun logVerboseInternal(message: String, fields: Map<String, String>?) {
        replayModule.logger.logVerboseInternal(message, fields)
    }

    override fun logDebugInternal(message: String, fields: Map<String, String>?) {
        replayModule.logger.logDebugInternal(message, fields)
    }

    override fun logErrorInternal(message: String, e: Throwable?, fields: Map<String, String>?) {
        replayModule.logger.logErrorInternal(message, e, fields)
    }

    private object WebSocketLogger : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("ReplayPreviewClient", "onClosed($webSocket, $code, $reason)")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("ReplayPreviewClient", "onClosing($webSocket, $code, $reason)")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.d("ReplayPreviewClient", "onFailure($webSocket, $t, $response)")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("ReplayPreviewClient", "onMessage($webSocket, $text)")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d("ReplayPreviewClient", "onMessage($webSocket, $bytes)")
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("ReplayPreviewClient", "onOpen($webSocket, $response)")
        }
    }
}
