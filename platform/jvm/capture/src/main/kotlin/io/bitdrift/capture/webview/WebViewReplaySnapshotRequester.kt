// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.replay.WebViewReplaySnapshot

/**
 * Asks the main frame of [webView] for a DOM snapshot each time session replay captures it.
 */
internal class WebViewReplaySnapshotRequester(
    private val webView: WebView,
    private val runtimeProvider: IRuntimeProvider,
) : Runnable {
    var replyProxy: JavaScriptReplyProxy? = null

    val isEnabled: Boolean
        get() = runtimeProvider.isRuntimeFeatureEnabled(RuntimeFeature.WEBVIEW_SESSION_REPLAY)

    @SuppressLint("RequiresFeature") // Only created once WEB_MESSAGE_LISTENER support is verified
    override fun run() {
        if (!isEnabled) {
            WebViewReplaySnapshot.clear(webView)
            return
        }
        val force = WebViewReplaySnapshot.of(webView) == null
        runCatching {
            replyProxy?.postMessage("""{"type":"replaySnapshotRequest","force":$force}""")
        }
    }
}
