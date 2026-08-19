// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Describes the strategy to use for session management.
 */
sealed class SessionStrategy {
    internal data class Configuration(
        val configuration: SessionConfiguration,
    ) : SessionStrategy()

    /**
     * Deprecated compatibility shim for a session that does not expire during a process but is
     * replaced when the SDK starts in a new process.
     *
     * [sessionIdGenerator] is retained for source compatibility but is no longer invoked. Capture
     * generates UUIDs for SDK-created sessions; use [SessionConfiguration.initialSessionId] when
     * an application needs to supply an initial ID.
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

    internal fun createSessionStrategyConfiguration(): SessionStrategyConfiguration =
        when (this) {
            is Configuration -> configuration.createSessionStrategyConfiguration()
            is Fixed ->
                SessionStrategyConfiguration(
                    initialSessionId = null,
                    inactivityTimeoutMilliseconds = null,
                    onSessionIdChanged = null,
                )
            is ActivityBased ->
                SessionStrategyConfiguration(
                    initialSessionId = null,
                    inactivityTimeoutMilliseconds = inactivityThresholdMins.minutes.inWholeMilliseconds,
                    onSessionIdChanged = onSessionIdChanged,
                )
        }
}
