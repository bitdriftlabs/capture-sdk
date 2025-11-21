// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.UUID

class SessionStrategyConfigurationTest {
    @Test
    fun fixedRegeneratesUuid() {
        val sessionStrategyConfiguration =
            SessionStrategyConfiguration.Fixed(
                sessionStrategy = SessionStrategy.Fixed(),
            )
        val newSessionId = sessionStrategyConfiguration.generateSessionId()

        // validate that the generated session id is a valid UUID
        UUID.fromString(newSessionId)
    }

    @Test
    fun fixedRegeneratesCustom() {
        var counter = 0
        val sessionIdGenerator = { (++counter).toString() }

        val sessionStrategyConfiguration =
            SessionStrategyConfiguration.Fixed(
                sessionStrategy = SessionStrategy.Fixed(sessionIdGenerator),
            )
        val newSessionId = sessionStrategyConfiguration.generateSessionId()

        assertThat(newSessionId).isEqualTo("1")
    }
}
