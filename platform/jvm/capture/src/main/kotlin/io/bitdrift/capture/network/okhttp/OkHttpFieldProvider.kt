// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request
import okhttp3.Response

/**
 * Provides additional custom fields to add to HTTP request and response logs automatically sent
 * by using [CaptureOkHttpEventListenerFactory].
 *
 * This interface allows you to provide fields for both requests and responses. You can implement
 * one or both methods depending on your needs. Methods you don't override will return empty maps.
 */
interface OkHttpFieldProvider {
    /**
     * Provides extra fields for a given request.
     *
     * @param request The HTTP request being logged.
     * @return a map of fields to add to the request log that will be sent
     * by [CaptureOkHttpEventListenerFactory] for this [request].
     */
    fun provideRequestFields(request: Request): Map<String, String> = emptyMap()

    /**
     * Provides extra fields for a given response.
     *
     * @param response The HTTP response received. The original request can be accessed via
     *                 [Response.request] when response is not null.
     * @return a map of fields to add to the response log that will be sent
     * by [CaptureOkHttpEventListenerFactory] for this [response].
     *
     * Note: The response body can be read from [response.body], but be aware that it may
     * have already been consumed by the application code. If you need to read the body,
     * consider using [response.peekBody] to avoid consuming the actual response body.
     */
    fun provideResponseFields(response: Response): Map<String, String> = emptyMap()
}

/**
 * Creates an [OkHttpFieldProvider] that only provides request fields.
 *
 * @param provideFields A function that provides extra fields for a given request.
 * @return An [OkHttpFieldProvider] that uses the provided function for request fields
 *         and returns empty map for response fields.
 */
fun okHttpFieldProvider(provideFields: (Request) -> Map<String, String>): OkHttpFieldProvider =
    object : OkHttpFieldProvider {
        override fun provideRequestFields(request: Request): Map<String, String> = provideFields(request)
    }

/**
 * Creates an [OkHttpFieldProvider] that provides both request and response fields.
 *
 * @param provideRequestFields A function that provides extra fields for a given request.
 * @param provideResponseFields A function that provides extra fields for a given response.
 * @return An [OkHttpFieldProvider] that uses the provided functions for both request and response fields.
 */
fun okHttpFieldProvider(
    provideRequestFields: (Request) -> Map<String, String>,
    provideResponseFields: (Response) -> Map<String, String>,
): OkHttpFieldProvider =
    object : OkHttpFieldProvider {
        override fun provideRequestFields(request: Request): Map<String, String> = provideRequestFields(request)

        override fun provideResponseFields(response: Response): Map<String, String> = provideResponseFields(response)
    }
