// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request

/**
 * Provides additional custom fields to add to http logs automatically sent
 * by using [CaptureOkHttpEventListenerFactory].
 */
fun interface OkHttpRequestFieldProvider {
    /**
     * @return a map of fields to add to the http log that will be sent
     * by [CaptureOkHttpEventListenerFactory] for this [request].
     */
    fun provideExtraFields(request: Request): Map<String, String>
}
