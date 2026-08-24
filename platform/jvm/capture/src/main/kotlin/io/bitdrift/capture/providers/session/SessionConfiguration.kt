// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

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
 * Without an inactivity timeout, Capture does not persist session IDs across SDK restarts.
 *
 * When [inactivityTimeout] is set, a period of inactivity rotates the session to an SDK-created
 * UUID. Calling `startNewSession` with a non-empty ID always uses that ID, including with an
 * inactivity timeout configured. Mixing caller-supplied IDs with SDK-created IDs is therefore
 * discouraged unless the application can handle both forms.
 * Empty initial and explicit IDs are treated as absent, so Capture generates a UUID instead.
 * Invalid inactivity timeouts are treated as absent, disabling activity-based rotation.
 *
 * [onSessionIdChanged] is invoked after Capture starts the initial session, rotates after
 * inactivity, and on every explicit session start. An explicit start invokes the callback even
 * when it supplies the current session ID. It is invoked on the Android main thread. When session
 * starts overlap, callbacks can arrive in a different order from their state transitions. Treat
 * the callback ID as belonging to that individual start; use the logger's
 * [io.bitdrift.capture.ILogger.sessionId] when the current session ID is required.
 *
 * @property initialSessionId Optional non-empty ID to use whenever no inactivity timeout is
 * configured, or to seed the first session when one is configured. When absent or empty, Capture
 * generates a UUID.
 * @property inactivityTimeout Optional non-negative, finite inactivity duration after which Capture
 * generates a new UUID session ID. Invalid values are stored as absent, disabling activity-based
 * rotation.
 * @property onSessionIdChanged Optional callback that receives the active session ID after each
 * session start or rotation, including explicit starts with the current ID.
 */
class SessionConfiguration(
    val initialSessionId: String? = null,
    inactivityTimeout: Duration? = null,
    val onSessionIdChanged: ((String) -> Unit)? = null,
) {
    val inactivityTimeout: Duration? = validatedInactivityTimeout(inactivityTimeout)

    /** Factory methods for Java-compatible session configurations. */
    companion object {
        /**
         * Creates a session configuration with an inactivity timeout for Java callers. Negative
         * values disable activity-based rotation.
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

    internal fun makeSessionCallback(): SessionCallback? = onSessionIdChanged?.let(::SessionCallback)

    private fun validatedInactivityTimeout(inactivityTimeout: Duration?): Duration? {
        if (inactivityTimeout == null) {
            return null
        }

        if (inactivityTimeout.isNegative() || !inactivityTimeout.isFinite()) {
            return null
        }

        return inactivityTimeout
    }
}
