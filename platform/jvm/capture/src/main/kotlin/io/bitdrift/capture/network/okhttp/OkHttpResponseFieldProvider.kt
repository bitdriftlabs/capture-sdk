// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Response

/**
 * Provides additional custom fields to add to HTTP response logs automatically sent
 * by using [CaptureOkHttpEventListenerFactory], allowing you to add fields based on the response,
 * such as custom error messages extracted from error response bodies, status codes, or response headers.
 */
fun interface OkHttpResponseFieldProvider {
    /**
     * @param response The HTTP response received, or null if the request failed before receiving a response.
     *                 The original request can be accessed via [Response.request] when response is not null.
     * @return a map of fields to add to the HTTP response log that will be sent
     * by [CaptureOkHttpEventListenerFactory] for this [response].
     *
     * Note: The response body can be read from [response.body], but be aware that it may
     * have already been consumed by the application code. If you need to read the body,
     * consider using [response.peekBody] to avoid consuming the actual response body.
     */
    fun provideExtraFields(response: Response): Map<String, String>
}
