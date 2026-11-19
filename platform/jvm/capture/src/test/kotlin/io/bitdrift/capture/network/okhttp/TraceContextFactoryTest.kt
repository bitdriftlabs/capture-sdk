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
        assertThat(TracePropagationMode.fromRuntimeValue("dd")).isEqualTo(TracePropagationMode.DD)
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

    @Test
    fun datadog_conversions_shouldConformToSpec() {
        // Test with max 64-bit values to ensure unsigned handling (Datadog uses unsigned 64-bit decimals)
        val traceId = "ffffffffffffffffffffffffffffffff"
        val spanId = "ffffffffffffffff"
        val context = TraceContext(traceId = traceId, spanId = spanId)

        // 2^64 - 1
        val expectedMax64 = "18446744073709551615"
        assertThat(context.traceIdAsDecimal()).isEqualTo(expectedMax64)
        assertThat(context.spanIdAsDecimal()).isEqualTo(expectedMax64)

        // Test with a specific known 128-bit trace ID
        val traceId128 = "88c131f5a4a41657a4cc039862759571"
        val context128 = TraceContext(traceId = traceId128, spanId = "639ec5ab80cb312d")

        // Lower 64 bits of traceId128: a4cc039862759571
        // a4cc039862759571 hex to decimal: 11874870270490940785
        assertThat(context128.traceIdAsDecimal()).isEqualTo("11874870270490940785")

        // spanId: 639ec5ab80cb312d
        // 639ec5ab80cb312d hex to decimal: 7178392196466028845
        assertThat(context128.spanIdAsDecimal()).isEqualTo("7178392196466028845")
    }
}
