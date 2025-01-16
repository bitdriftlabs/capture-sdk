// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import java.util.UUID

/**
 * Describes the strategy to use for session management.
 */
sealed class SessionStrategy {
    /**
     * A session strategy that never expires the session ID but does not survive process restart.
     *
     * The initial session ID is retrieved by calling the passed closure.
     *
     * Whenever a new session is manually started via `startNewSession` method call, the closure is
     * invoked to generate a new session ID.
     * @param sessionIdGenerator The callback that will invoked to obtain the session ID to use. Upon the initialization
     *  of the logger the function is called on the thread that's used to configure the logger.
     *  Subsequent function calls are performed every time [io.bitdrift.Bitdrift.Logger.startNewSession]
     *  method is called using the thread on which the method is called.
     */
    data class Fixed
        @JvmOverloads
        constructor(
            val sessionIdGenerator: () -> String = { UUID.randomUUID().toString() },
        ) : SessionStrategy()

    /**
     * A session strategy that generates a new session ID after a certain period of app inactivity.
     *
     * The activity is measured by the number of minutes elapsed since the last log. Session ID is persisted
     * to disk and survives app restarts.
     *
     * Each log emitted by the SDK - including the logs emitted by session replay and resource monitoring
     * features - counts as activity
     * @param inactivityThresholdMins the amount of minutes of inactivity after which a new session Id changes.
     * The default value is 30 minutes.
     * @param onSessionIdChanged optional callback that is invoked with the new value every time the session Id changes.
     *  This callback is invoked in the main thread.
     */
    data class ActivityBased
        @JvmOverloads
        constructor(
            val inactivityThresholdMins: Long = 30,
            val onSessionIdChanged: ((String) -> Unit)? = null,
        ) : SessionStrategy()

    internal fun createSessionStrategyConfiguration(onSessionIdChanged: (String) -> Unit): SessionStrategyConfiguration =
        when (this) {
            is Fixed -> SessionStrategyConfiguration.Fixed(this, onSessionIdChanged)
            is ActivityBased -> SessionStrategyConfiguration.ActivityBased(this, onSessionIdChanged)
        }
}
