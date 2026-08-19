// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

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
