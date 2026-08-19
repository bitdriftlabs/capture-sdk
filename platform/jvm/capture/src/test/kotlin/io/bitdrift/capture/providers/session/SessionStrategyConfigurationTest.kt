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

class SessionStrategyConfigurationTest {
    @Test
    fun fixedCompatibilityShimDoesNotInvokeSessionIdGenerator() {
        var generatorCalls = 0
        val sessionStrategyConfiguration =
            SessionStrategy.Fixed { (++generatorCalls).toString() }.createSessionStrategyConfiguration()

        assertThat(generatorCalls).isZero()
        assertThat(sessionStrategyConfiguration.initialSessionId()).isNull()
        assertThat(sessionStrategyConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(-1)
    }

    @Test
    fun preservesSubMinuteInactivityTimeout() {
        val sessionStrategyConfiguration =
            SessionConfiguration(inactivityTimeout = 30.seconds).createSessionStrategyConfiguration()

        assertThat(sessionStrategyConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(30_000)
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

        val sessionStrategyConfiguration =
            SessionConfiguration
                .withInactivityTimeout(
                    inactivityTimeoutMilliseconds = 30_000,
                    initialSessionId = "initial",
                ).createSessionStrategyConfiguration()

        assertThat(sessionStrategyConfiguration.initialSessionId()).isEqualTo("initial")
        assertThat(sessionStrategyConfiguration.inactivityTimeoutMilliseconds()).isEqualTo(30_000)
    }
}
