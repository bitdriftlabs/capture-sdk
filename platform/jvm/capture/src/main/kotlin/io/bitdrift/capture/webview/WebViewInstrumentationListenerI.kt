// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.view.View
import android.webkit.WebView
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.replay.IReplayViewListener
import java.util.Collections
import java.util.WeakHashMap

/**
 * Implements [IReplayViewListener] to automatically instrument WebViews discovered
 * during session replay view traversal.
 *
 * Uses a WeakHashMap to track already-instrumented WebViews to avoid re-instrumenting
 * the same WebView multiple times. The WeakHashMap allows WebViews to be garbage
 * collected when no longer referenced elsewhere.
 */
internal class WebViewInstrumentationListenerI(
    private val logger: ILogger?,
) : IReplayViewListener {
    private val instrumentedWebViews: MutableSet<WebView> =
        Collections.newSetFromMap(WeakHashMap())

    override fun onViewFound(view: View) {
        if (view !is WebView) return
        if (instrumentedWebViews.contains(view)) return

        instrumentedWebViews.add(view)
        WebViewCapture.instrument(view, logger)
    }
}
