// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.utils.invokeCatchingOrThrowOnDebug

/** Dispatches session changes from the Rust core to the app callback on the Android main thread. */
internal class SessionCallback(
    private val onSessionIdChanged: (String) -> Unit,
    private val mainThreadHandlerOverride: MainThreadHandler? = null,
) {
    private val mainThreadHandler by lazy { mainThreadHandlerOverride ?: MainThreadHandler() }

    fun sessionIdChanged(sessionId: String) {
        mainThreadHandler.run {
            onSessionIdChanged.invokeCatchingOrThrowOnDebug(sessionId)
        }
    }
}
