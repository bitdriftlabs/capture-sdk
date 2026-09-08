// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.extension.InstrumentationExtension.WebViewAutomaticInstrumentationMode
import org.gradle.api.GradleException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebViewInstrumentationModeResolutionTest {
    @Test
    fun `unset properties disable automatic webview instrumentation`() {
        assertNull(resolveWebViewAutomaticInstrumentationMode(legacyEnabled = false, configuredMode = null))
    }

    @Test
    fun `legacy enabled maps to full`() {
        assertEquals(
            WebViewAutomaticInstrumentationMode.FULL,
            resolveWebViewAutomaticInstrumentationMode(legacyEnabled = true, configuredMode = null),
        )
    }

    @Test
    fun `configured mode is retained`() {
        assertEquals(
            WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED,
            resolveWebViewAutomaticInstrumentationMode(
                legacyEnabled = false,
                configuredMode = WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED,
            ),
        )
    }

    @Test
    fun `legacy and configured modes cannot be combined`() {
        assertFailsWith<GradleException> {
            resolveWebViewAutomaticInstrumentationMode(
                legacyEnabled = true,
                configuredMode = WebViewAutomaticInstrumentationMode.FULL,
            )
        }
    }
}
