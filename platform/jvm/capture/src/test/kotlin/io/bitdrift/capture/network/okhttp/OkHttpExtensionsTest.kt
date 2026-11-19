// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class OkHttpExtensionsTest {
    @Test
    fun `enableFastFallbackIfAvailable does not throw on OkHttp 4x`() {
        val okHttpBuilder = OkHttpClient.Builder()

        val result = okHttpBuilder.enableFastFallbackIfAvailable()

        assertThat(okHttpBuilder).isEqualTo(result)
    }
}
