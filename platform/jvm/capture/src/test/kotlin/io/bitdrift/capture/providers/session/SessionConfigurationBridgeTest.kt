// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers.session

import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SessionConfigurationBridgeTest {
    @Test
    fun fixedCompatibilityShimDisablesInactivityRotation() {
        val sessionConfiguration =
            SessionStrategy.Fixed().createSessionConfigurationBridge()

        assertThat(sessionConfiguration.initialSessionId()).isNull()
        assertThat(sessionConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(-1)
    }

    @Test
    fun preservesSubMinuteInactivityTimeout() {
        val sessionConfiguration =
            SessionConfiguration(inactivityTimeout = 30.seconds).createSessionConfigurationBridge()

        assertThat(sessionConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(30_000)
    }

    @Test
    fun keepsNoArgumentSessionStartMethodsForJava() {
        assertThat(Capture.Logger::class.java.getMethod("startNewSession")).isNotNull()
        assertThat(ILogger::class.java.getMethod("startNewSession")).isNotNull()
    }

    @Test
    fun createsInactivityConfigurationForJava() {
        assertThat(
            SessionConfiguration::class.java.getMethod(
                "withInactivityTimeout",
                Long::class.javaPrimitiveType,
                String::class.java,
            ),
        ).isNotNull()

        val sessionConfiguration =
            SessionConfiguration
                .withInactivityTimeout(
                    inactivityTimeoutMilliseconds = 30_000,
                    initialSessionId = "initial",
                ).createSessionConfigurationBridge()

        assertThat(sessionConfiguration.initialSessionId()).isEqualTo("initial")
        assertThat(sessionConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(30_000)
    }
}
