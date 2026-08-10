// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

package io.bitdrift.capture.providers.session

import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.utils.invokeCatchingOrThrowOnDebug

/** JNI-facing representation of [SessionConfiguration]. */
internal open class SessionStrategyConfiguration(
    private val initialSessionId: String?,
    private val inactivityTimeoutMins: Long?,
    private val onSessionIdChanged: ((String) -> Unit)?,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) {
    fun initialSessionId(): String? = initialSessionId

    /** A negative value means activity-based refresh is disabled. */
    fun inactivityTimeoutMins(): Long = inactivityTimeoutMins ?: -1L

    fun sessionIdChanged(sessionId: String) {
        mainThreadHandler.run {
            onSessionIdChanged.invokeCatchingOrThrowOnDebug(sessionId)
        }
    }

    /** Deprecated bridge adapter retained for source compatibility. */
    class Fixed(
        private val sessionStrategy: SessionStrategy.Fixed,
    ) : SessionStrategyConfiguration(
        initialSessionId = null,
        inactivityTimeoutMins = null,
        onSessionIdChanged = null,
    ) {
        fun generateSessionId(): String = sessionStrategy.sessionIdGenerator()
    }

    /** Deprecated bridge adapter retained for source compatibility. */
    class ActivityBased(
        private val sessionStrategy: SessionStrategy.ActivityBased,
        mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    ) : SessionStrategyConfiguration(
        initialSessionId = null,
        inactivityTimeoutMins = sessionStrategy.inactivityThresholdMins,
        onSessionIdChanged = sessionStrategy.onSessionIdChanged,
        mainThreadHandler = mainThreadHandler,
    )
}
