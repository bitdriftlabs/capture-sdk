// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

/**
 * Metrics collected for the execution of an HTTP request.
 */
data class HttpRequestMetrics @JvmOverloads constructor(
    /**
     * The over-the-wire size of the sent request body.
     */
    internal val requestBodyBytesSentCount: Long? = null,
    /**
     * The over-the-wire size of the received response body.
     */
    internal val responseBodyBytesReceivedCount: Long? = null,

    /**
     * The size of sent HTTP request headers.
     */
    internal val requestHeadersBytesCount: Long? = null,
    /**
     * The size of received HTTP request headers.
     */
    internal val responseHeadersBytesCount: Long? = null,

    /**
     * The cumulative duration of all DNS resolution query(ies) performed during the execution
     * of a given HTTP request.
     */
    internal var dnsResolutionDurationMs: Long? = null,
)
