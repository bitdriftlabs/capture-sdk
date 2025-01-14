// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import io.bitdrift.capture.common.MainThreadHandler

internal sealed class SessionStrategyConfiguration {
    data class Fixed(
        val sessionStrategy: SessionStrategy.Fixed,
        val onSessionIdChanged: (String) -> Unit,
    ) : SessionStrategyConfiguration() {
        fun generateSessionId(): String {
            val newSessionId = sessionStrategy.sessionIdGenerator()
            onSessionIdChanged(newSessionId)
            return newSessionId
        }
    }

    data class ActivityBased(
        val sessionStrategy: SessionStrategy.ActivityBased,
        val onSessionIdChanged: (String) -> Unit,
        val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    ) : SessionStrategyConfiguration() {
        fun inactivityThresholdMins(): Long = sessionStrategy.inactivityThresholdMins

        fun sessionIdChanged(sessionId: String) {
            onSessionIdChanged(sessionId)
            mainThreadHandler.runCatching {
                sessionStrategy.onSessionIdChanged?.invoke(sessionId)
            }
        }
    }
}
