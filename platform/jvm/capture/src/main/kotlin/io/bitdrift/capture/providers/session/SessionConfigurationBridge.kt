// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

package io.bitdrift.capture.providers.session

import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.utils.invokeCatchingOrThrowOnDebug

/** JNI-facing representation of [SessionConfiguration]. */
internal open class SessionConfigurationBridge(
    private val initialSessionId: String?,
    private val inactivityTimeoutMilliseconds: Long?,
    private val onSessionIdChanged: ((String) -> Unit)?,
    private val mainThreadHandlerOverride: MainThreadHandler? = null,
) {
    private val mainThreadHandler by lazy { mainThreadHandlerOverride ?: MainThreadHandler() }

    fun initialSessionId(): String? = initialSessionId

    /** A negative value means inactivity-driven rotation is disabled. */
    fun inactivityTimeoutMilliseconds(): Long = inactivityTimeoutMilliseconds ?: -1L

    fun sessionIdChanged(sessionId: String) {
        onSessionIdChanged?.let { callback ->
            mainThreadHandler.run {
                callback.invokeCatchingOrThrowOnDebug(sessionId)
            }
        }
    }
}
