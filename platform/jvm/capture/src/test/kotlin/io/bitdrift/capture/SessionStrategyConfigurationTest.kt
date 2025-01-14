// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.session.SessionStrategyConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.UUID

class SessionStrategyConfigurationTest {
    @Test
    fun fixedRegeneratesUuidAndPropagatesNewSession() {
        var propagatedSession: String? = null

        val sessionStrategyConfiguration =
            SessionStrategyConfiguration.Fixed(
                sessionStrategy = SessionStrategy.Fixed(),
                onSessionIdChanged = { sessionId -> propagatedSession = sessionId },
            )
        val newSessionId = sessionStrategyConfiguration.generateSessionId()

        // validate that the generated session id is a valid UUID
        val newSessionUuid = UUID.fromString(newSessionId)
        val propagatedUuid = UUID.fromString(propagatedSession)
        assertThat(newSessionUuid).isEqualTo(propagatedUuid)
    }

    @Test
    fun fixedRegeneratesCustomAndPropagatesNewSession() {
        var counter = 0
        val sessionIdGenerator = { (++counter).toString() }
        var propagatedSession: String? = null

        val sessionStrategyConfiguration =
            SessionStrategyConfiguration.Fixed(
                sessionStrategy = SessionStrategy.Fixed(sessionIdGenerator),
                onSessionIdChanged = { sessionId -> propagatedSession = sessionId },
            )
        val newSessionId = sessionStrategyConfiguration.generateSessionId()

        assertThat(newSessionId).isEqualTo("1")
        assertThat(newSessionId).isEqualTo(propagatedSession)
    }

    @Test
    fun activityBasedDoubleDispatchCallbacks() {
        val oneMinute = 1L
        var propagatedSession1: String? = null
        var propagatedSession2: String? = null
        val expectedSessionId = "newSessionId"

        val sessionStrategyConfiguration =
            SessionStrategyConfiguration.ActivityBased(
                sessionStrategy =
                    SessionStrategy.ActivityBased(
                        inactivityThresholdMins = oneMinute,
                        onSessionIdChanged = { sessionId -> propagatedSession2 = sessionId },
                    ),
                onSessionIdChanged = { sessionId -> propagatedSession1 = sessionId },
                mainThreadHandler = Mocks.sameThreadHandler,
            )
        sessionStrategyConfiguration.sessionIdChanged(expectedSessionId)

        assertThat(propagatedSession1).isEqualTo(expectedSessionId)
        assertThat(propagatedSession2).isEqualTo(expectedSessionId)
        assertThat(sessionStrategyConfiguration.inactivityThresholdMins()).isEqualTo(oneMinute)
    }
}
