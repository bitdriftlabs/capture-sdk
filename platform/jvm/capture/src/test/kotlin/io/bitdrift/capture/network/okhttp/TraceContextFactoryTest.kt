// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.common.RuntimeStringConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TraceContextFactoryTest {
    private val traceContextFactory = TraceContextFactory()

    @Test
    fun generateTraceContext_shouldCreateAsExpectedFormats() {
        val context = traceContextFactory.generateTraceContext()

        assertThat(context.traceId).hasSize(32)
        assertThat(context.traceId).matches("^[0-9a-f]{32}$")

        assertThat(context.spanId).hasSize(16)
        assertThat(context.spanId).matches("^[0-9a-f]{16}$")
    }

    @Test
    fun tracePropagationMode_fromRuntimeValue_shouldMapKnownValues() {
        assertThat(TracePropagationMode.fromRuntimeValue("none")).isEqualTo(TracePropagationMode.NONE)
        assertThat(TracePropagationMode.fromRuntimeValue("w3c")).isEqualTo(TracePropagationMode.W3C)
        assertThat(TracePropagationMode.fromRuntimeValue("b3-single")).isEqualTo(TracePropagationMode.B3_SINGLE)
        assertThat(TracePropagationMode.fromRuntimeValue("b3-multi")).isEqualTo(TracePropagationMode.B3_MULTI)
    }

    @Test
    fun tracePropagationMode_fromRuntimeValue_shouldNormalizeWhitespaceAndCase() {
        assertThat(TracePropagationMode.fromRuntimeValue("  W3C  ")).isEqualTo(TracePropagationMode.W3C)
        assertThat(TracePropagationMode.fromRuntimeValue("\tb3-single\n")).isEqualTo(TracePropagationMode.B3_SINGLE)
        assertThat(TracePropagationMode.fromRuntimeValue(" B3-MULTI ")).isEqualTo(TracePropagationMode.B3_MULTI)
    }

    @Test
    fun tracePropagationMode_fromRuntimeValue_whenUnknown_shouldDefaultToNone() {
        assertThat(TracePropagationMode.fromRuntimeValue("invalid")).isEqualTo(TracePropagationMode.NONE)
        assertThat(TracePropagationMode.fromRuntimeValue("disabled")).isEqualTo(TracePropagationMode.NONE)
        assertThat(TracePropagationMode.fromRuntimeValue(""))
            .isEqualTo(TracePropagationMode.NONE)
    }

    @Test
    fun tracePropagationMode_runtimeConfig_shouldDefaultToW3C() {
        assertThat(RuntimeStringConfig.TRACE_PROPAGATION_MODE.defaultValue).isEqualTo("w3c")
    }
}
