// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import androidx.annotation.VisibleForTesting
import okhttp3.OkHttpClient

/**
 * Builds the shared OkHttpClient instance used internally by the SDK,
 * with Happy Eyeballs (RFC 8305) fast fallback enabled when available.
 */
internal fun buildSharedOkHttpClient(): OkHttpClient = OkHttpClient.Builder().enableFastFallbackIfAvailable().build()

/**
 * If a consumer app is on OkHttp 5.0.* or up make sure fast fallback is enabled.
 */
@VisibleForTesting
internal fun OkHttpClient.Builder.enableFastFallbackIfAvailable(): OkHttpClient.Builder {
    try {
        val method = OkHttpClient.Builder::class.java.getMethod("fastFallback", Boolean::class.javaPrimitiveType)
        method.invoke(this, true)
    } catch (_: Exception) {
        // OkHttp < 5.x, fast fallback not available
    }
    return this
}
