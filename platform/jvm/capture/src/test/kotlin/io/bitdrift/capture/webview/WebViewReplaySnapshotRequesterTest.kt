// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.webkit.JavaScriptReplyProxy
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.replay.WebViewReplaySnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class WebViewReplaySnapshotRequesterTest {
    private val webView = WebView(ApplicationProvider.getApplicationContext())
    private val replyProxy: JavaScriptReplyProxy = mock()
    private val runtimeProvider: IRuntimeProvider = mock()
    private val requester =
        WebViewReplaySnapshotRequester(webView, runtimeProvider).also {
            it.replyProxy = replyProxy
        }

    @Test
    fun run_withoutSnapshot_shouldForceSnapshot() {
        enableSessionReplay(true)

        requester.run()

        verify(replyProxy).postMessage("""{"type":"replaySnapshotRequest","force":true}""")
    }

    @Test
    fun run_withSnapshot_shouldRequestSnapshotOnlyIfPageChanged() {
        enableSessionReplay(true)
        WebViewReplaySnapshot.attach(webView, WebViewReplaySnapshot(1, 1, IntArray(0)))

        requester.run()

        verify(replyProxy).postMessage("""{"type":"replaySnapshotRequest","force":false}""")
    }

    @Test
    fun run_whenRemotelyDisabled_shouldClearSnapshotWithoutRequesting() {
        enableSessionReplay(false)
        WebViewReplaySnapshot.attach(webView, WebViewReplaySnapshot(1, 1, IntArray(0)))

        requester.run()

        assertThat(WebViewReplaySnapshot.of(webView)).isNull()
        verify(replyProxy, never()).postMessage(any<String>())
    }

    private fun enableSessionReplay(enabled: Boolean) {
        whenever(runtimeProvider.isRuntimeFeatureEnabled(RuntimeFeature.WEBVIEW_SESSION_REPLAY)).thenReturn(enabled)
    }
}
