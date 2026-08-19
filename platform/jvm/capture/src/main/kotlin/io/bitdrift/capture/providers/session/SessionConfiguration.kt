// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.

package io.bitdrift.capture.providers.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Configures the Capture session lifecycle.
 *
 * This is the canonical session API. [SessionStrategy] is retained only as a compatibility shim.
 *
 * Capture creates a session when the SDK starts and whenever
 * [io.bitdrift.capture.Capture.Logger.startNewSession] is called. An SDK-created session ID is a
 * UUID. Without [inactivityTimeout], Capture uses [initialSessionId] whenever the SDK starts; it
 * never reuses a persisted session. With [inactivityTimeout], [initialSessionId] seeds only the
 * first session. Later SDK starts reuse the persisted session while it remains active and create
 * an SDK UUID only after the inactivity period has elapsed.
 *
 * When [inactivityTimeout] is set, a period of inactivity rotates the session to an SDK-created
 * UUID. Calling `startNewSession` with a non-empty ID always uses that ID, including with an
 * inactivity timeout configured. Mixing caller-supplied IDs with SDK-created IDs is therefore
 * discouraged unless the application can handle both forms.
 * Empty initial and explicit IDs are treated as absent, so Capture generates a UUID instead.
 *
 * [onSessionIdChanged] is invoked after Capture updates its in-memory session ID and schedules
 * best-effort persistence for the initial session, inactivity-driven rotations, and every explicit
 * session start. It is always invoked asynchronously on the Android main thread.
 *
 * @property initialSessionId Optional non-empty ID to use whenever no inactivity timeout is
 * configured, or to seed the first session when one is configured. When absent or empty, Capture
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
    companion object {
        /**
         * Creates a session configuration with an inactivity timeout for Java callers.
         */
        @JvmStatic
        @JvmOverloads
        fun withInactivityTimeout(
            inactivityTimeoutMilliseconds: Long,
            initialSessionId: String? = null,
        ): SessionConfiguration =
            SessionConfiguration(
                initialSessionId = initialSessionId,
                inactivityTimeout = inactivityTimeoutMilliseconds.milliseconds,
            )
    }

    internal fun createSessionStrategyConfiguration() =
        SessionStrategyConfiguration(
            initialSessionId = initialSessionId,
            inactivityTimeoutMilliseconds = inactivityTimeout?.inWholeMilliseconds,
            onSessionIdChanged = onSessionIdChanged,
        )
}
