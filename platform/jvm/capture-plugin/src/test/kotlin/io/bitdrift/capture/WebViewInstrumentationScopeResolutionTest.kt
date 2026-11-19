// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.extension.InstrumentationExtension.WebViewAutomaticInstrumentationScope
import org.gradle.api.GradleException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebViewInstrumentationScopeResolutionTest {
    @Test
    fun `unset properties disable automatic webview instrumentation`() {
        assertNull(resolveWebViewAutomaticInstrumentationScope(legacyEnabled = false, configuredScope = null))
    }

    @Test
    fun `legacy enabled maps to javascript enabled only`() {
        assertEquals(
            WebViewAutomaticInstrumentationScope.JS_ENABLED,
            resolveWebViewAutomaticInstrumentationScope(legacyEnabled = true, configuredScope = null),
        )
    }

    @Test
    fun `configured mode is retained`() {
        assertEquals(
            WebViewAutomaticInstrumentationScope.JS_ENABLED,
            resolveWebViewAutomaticInstrumentationScope(
                legacyEnabled = false,
                configuredScope = WebViewAutomaticInstrumentationScope.JS_ENABLED,
            ),
        )
    }

    @Test
    fun `legacy and configured modes cannot be combined`() {
        assertFailsWith<GradleException> {
            resolveWebViewAutomaticInstrumentationScope(
                legacyEnabled = true,
                configuredScope = WebViewAutomaticInstrumentationScope.ALL,
            )
        }
    }
}
