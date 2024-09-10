// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

/**
 * Represents an HTTP response.
 * @param result: The result of the HTTP request.
 * @param host: The host a given response came from, if any.
 * @param path: The path a given response came from, if any.
 * @param query: The path a given query came from, if any.
 * @param headers: The response headers, if any.
 * @param statusCode: The status code received from the server, if any.
 * @param error: The error received as the result of performing an HTTP request, if any.
 */
data class HttpResponse @JvmOverloads constructor(
    val result: HttpResult,
    val host: String? = null,
    val path: HttpUrlPath? = null,
    val query: String? = null,
    val headers: Map<String, String>? = null,
    val statusCode: Int? = null,
    val error: Throwable? = null,
) {
    /**
     * Represents the result of an http request operation
     */
    enum class HttpResult {
        /**
         * Represents a successful http request operation
         */
        SUCCESS,
        /**
         * Represents a failed http request operation
         */
        FAILURE,
        /**
         * Represents an interrupted or incomplete http request operation
         */
        CANCELED,
    }
}
