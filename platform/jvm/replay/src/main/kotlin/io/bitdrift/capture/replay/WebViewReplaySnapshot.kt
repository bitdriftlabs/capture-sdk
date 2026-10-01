// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.view.View

/**
 * Wireframe of the visible DOM of a WebView, as reported by the Capture WebView bridge.
 *
 * @param viewportWidth The layout viewport width in CSS pixels
 * @param viewportHeight The layout viewport height in CSS pixels
 * @param elements Flat `[type, x, y, width, height, ...]` list in CSS pixels relative to the
 * viewport, ordered back to front. `type` is a [ReplayType.typeValue].
 */
class WebViewReplaySnapshot(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val elements: IntArray,
) {
    /**
     * Stores and retrieves the latest snapshot of a WebView.
     */
    companion object {
        private const val TAG_KEY_SNAPSHOT = 0x62647270
        private const val TAG_KEY_REQUESTER = 0x62647271

        /**
         * Replaces the latest snapshot rendered by session replay for [webView].
         */
        @JvmStatic
        fun attach(
            webView: View,
            snapshot: WebViewReplaySnapshot,
        ) {
            webView.setTag(TAG_KEY_SNAPSHOT, snapshot)
        }

        /**
         * Removes the snapshot of [webView], e.g. when it starts loading a new document.
         */
        @JvmStatic
        fun clear(webView: View) {
            webView.setTag(TAG_KEY_SNAPSHOT, null)
        }

        /**
         * Registers the [requester] session replay runs on every frame it captures [webView] in,
         * asking for a fresh snapshot to render in the next frame. Passing null unregisters it.
         */
        @JvmStatic
        fun setRequester(
            webView: View,
            requester: Runnable?,
        ) {
            webView.setTag(TAG_KEY_REQUESTER, requester)
        }

        /**
         * The latest snapshot of [webView], or null when none was reported for its current document.
         */
        @JvmStatic
        fun of(webView: View): WebViewReplaySnapshot? = webView.getTag(TAG_KEY_SNAPSHOT) as? WebViewReplaySnapshot

        internal fun request(view: View) {
            (view.getTag(TAG_KEY_REQUESTER) as? Runnable)?.run()
        }
    }
}
