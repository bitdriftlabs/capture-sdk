// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

package io.bitdrift.capture.providers.session

import kotlin.time.Duration

/**
 * Configures the Capture session lifecycle.
 *
 * This is the canonical session API. [SessionStrategy] is retained only as a compatibility shim.
 *
 * Capture creates a session when the SDK starts and whenever
 * [io.bitdrift.capture.Capture.Logger.startNewSession] is called. An SDK-created session ID is a
 * UUID. A caller-supplied ID is used exactly for the initialization or explicit session start in
 * which it is provided; it does not establish an ownership mode for future sessions.
 *
 * When [inactivityTimeout] is set, a period of inactivity rotates the session to an SDK-created
 * UUID. Calling `startNewSession` with a non-null ID always uses that ID, including with an
 * inactivity timeout configured. Mixing caller-supplied IDs with SDK-created IDs is therefore
 * discouraged unless the application can handle both forms.
 *
 * [onSessionIdChanged] is invoked after Capture durably updates the session ID for the initial
 * session, inactivity-driven rotations, and every explicit session start. It is always invoked
 * asynchronously on the Android main thread.
 *
 * @property initialSessionId Optional ID to use for the initial session. When absent, Capture
 * generates a UUID.
 * @property inactivityTimeout Optional inactivity duration after which Capture generates a new
 * UUID session ID. When absent, activity-based rotation is disabled.
 * @property onSessionIdChanged Optional callback that receives each new session ID.
 */
data class SessionConfiguration(
    val initialSessionId: String? = null,
    val inactivityTimeout: Duration? = null,
    val onSessionIdChanged: ((String) -> Unit)? = null,
) {
    internal fun createSessionStrategyConfiguration() =
        SessionStrategyConfiguration(
            initialSessionId = initialSessionId,
            inactivityTimeoutMins = inactivityTimeout?.inWholeMinutes,
            onSessionIdChanged = onSessionIdChanged,
        )
}
