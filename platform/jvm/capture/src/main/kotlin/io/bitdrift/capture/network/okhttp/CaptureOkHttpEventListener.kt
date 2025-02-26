// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.ILogger
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpRequestMetrics
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal class CaptureOkHttpEventListener internal constructor(
    private val logger: ILogger?,
    private val clock: IClock,
    private val targetEventListener: EventListener?,
) : EventListener() {
    private var requestBodyBytesSentCount: Long = 0
    private var responseBodyBytesReceivedCount: Long = 0

    /**
     *  It's number of bytes required to encode these headers using HTTP/1.1. This is also the
     *  approximate size of HTTP/2 headers before they are compressed with HPACK.
     */
    private var requestHeadersBytesCount: Long = 0

    /**
     *  It's number of bytes required to encode these headers using HTTP/1.1. This is also the
     *  approximate size of HTTP/2 headers before they are compressed with HPACK.
     */
    private var responseHeadersBytesCount: Long = 0

    /**
     * The cumulative duration of all DNS resolution query(ies) performed during the execution
     * of a given HTTP request. Due to retries of different routes or redirects a single pair of
     * `callStart` and `callEnd`/`callFailed` may include multiple DNS queries.
     */
    private var dnsResolutionDurationMs: Long? = null

    /**
     * The cumulative duration of all TLS handshakes performed during the execution of a given HTTP
     * request. Due to retries of different routes or redirects a single pair of
     * `callStart` and `callEnd`/`callFailed` may include multiple TLS handshakes.
     */
    private var tlsDurationMs: Long? = null

    /**
     * The cumulative duration of all TCP handshakes performed during the execution of a given
     * HTTP request. Due to retries of different routes or redirects a single pair of
     * `callStart` and `callEnd`/`callFailed` may include multiple TCP handshakes.
     */
    private var tcpDurationMs: Long? = null

    /**
     * The duration between the `callStart` and the first DNS resolution.
     */
    private var fetchInitializationMs: Long? = null

    /**
     * The cumulative duration of all responses from the time the request is sent to the time we get
     * the first byte from the server. Due to retries of different routes or redirects a single pair
     * of `callStart` and `callEnd`/`callFailed` may include multiple request/responses.
     */
    private var responseLatencyMs: Long? = null

    private var connectStartTimeMs: Long? = null
    private var dnsStartTimeMs: Long? = null
    private var callStartTimeMs: Long = 0
    private var tlsStartTimeMs: Long? = null
    private var requestEndTimeMs: Long? = null

    private var requestInfo: HttpRequestInfo? = null
    private var lastResponse: Response? = null

    override fun callStart(call: Call) {
        runCatching { targetEventListener?.callStart(call) }
        if (call.shouldIgnoreCurrentPath()) {
            return
        }

        callStartTimeMs = clock.elapsedRealtime()

        val request = call.request()

        val pathTemplateHeaderValues = request.headers.values("x-capture-path-template")
        val pathTemplate =
            if (pathTemplateHeaderValues.isEmpty()) {
                null
            } else {
                pathTemplateHeaderValues.joinToString(",")
            }

        val bytesExpectedToSendCount =
            if (request.body == null) {
                // If there is no body set the number of body bytes to send to 0
                0
            } else {
                request.body?.contentLength().validateLength()
            }

        val requestInfo =
            HttpRequestInfo(
                host = request.url.host,
                method = request.method,
                path =
                    HttpUrlPath(
                        request.url.encodedPath,
                        pathTemplate,
                    ),
                query = request.url.query,
                headers = request.headers.toMap(),
                bytesExpectedToSendCount = bytesExpectedToSendCount,
            )

        this.requestInfo = requestInfo
        logger?.log(requestInfo)
    }

    override fun proxySelectStart(
        call: Call,
        url: HttpUrl,
    ) {
        runCatching { targetEventListener?.proxySelectStart(call, url) }
    }

    override fun proxySelectEnd(
        call: Call,
        url: HttpUrl,
        proxies: List<Proxy>,
    ) {
        runCatching { targetEventListener?.proxySelectEnd(call, url, proxies) }
    }

    override fun dnsStart(
        call: Call,
        domainName: String,
    ) {
        runCatching { targetEventListener?.dnsStart(call, domainName) }
        val elapsed = clock.elapsedRealtime()

        // Calculate initialization time as the first dnsStart - callStart
        if (fetchInitializationMs == null) {
            fetchInitializationMs = (elapsed - callStartTimeMs)
        }

        dnsStartTimeMs = elapsed
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>,
    ) {
        runCatching { targetEventListener?.dnsEnd(call, domainName, inetAddressList) }
        val elapsed = clock.elapsedRealtime()

        val dnsStartTimeMs = dnsStartTimeMs ?: return
        val currentDnsDurationMs = (elapsed - dnsStartTimeMs)
        dnsResolutionDurationMs = (dnsResolutionDurationMs ?: 0) + currentDnsDurationMs
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
    ) {
        runCatching { targetEventListener?.connectStart(call, inetSocketAddress, proxy) }

        connectStartTimeMs = clock.elapsedRealtime()
    }

    override fun secureConnectStart(call: Call) {
        runCatching { targetEventListener?.secureConnectStart(call) }
        val elapsed = clock.elapsedRealtime()

        // If this is an https request, we measure the tcp time as tlsStart - connectStart
        val connectStartTimeMs = connectStartTimeMs ?: return
        val currentTCPDurationMs = (elapsed - connectStartTimeMs)
        tcpDurationMs = (tcpDurationMs ?: 0) + currentTCPDurationMs

        tlsStartTimeMs = elapsed
    }

    override fun secureConnectEnd(
        call: Call,
        handshake: Handshake?,
    ) {
        runCatching { targetEventListener?.secureConnectEnd(call, handshake) }
        val elapsed = clock.elapsedRealtime()

        val tlsStartTimeMs = tlsStartTimeMs ?: return
        val currentTLSDurationMs = (elapsed - tlsStartTimeMs)
        tlsDurationMs = (tlsDurationMs ?: 0) + currentTLSDurationMs
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        runCatching { targetEventListener?.connectEnd(call, inetSocketAddress, proxy, protocol) }
        val elapsed = clock.elapsedRealtime()

        // If this is not an https request (ie there wasn't a handshake), we measure the tcp time as
        // connectEnd - connectStart.
        if (tlsStartTimeMs == null) {
            val connectStartTimeMs = connectStartTimeMs ?: return
            val currentTCPDurationMs = (elapsed - connectStartTimeMs)
            tcpDurationMs = (tcpDurationMs ?: 0) + currentTCPDurationMs
        }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        runCatching { targetEventListener?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe) }
    }

    override fun connectionAcquired(
        call: Call,
        connection: Connection,
    ) {
        runCatching { targetEventListener?.connectionAcquired(call, connection) }
    }

    override fun connectionReleased(
        call: Call,
        connection: Connection,
    ) {
        runCatching { targetEventListener?.connectionReleased(call, connection) }
    }

    override fun requestHeadersStart(call: Call) {
        runCatching { targetEventListener?.requestHeadersStart(call) }
    }

    override fun requestHeadersEnd(
        call: Call,
        request: Request,
    ) {
        runCatching { targetEventListener?.requestHeadersEnd(call, request) }

        // Measure it here too in case the request has no body
        requestEndTimeMs = clock.elapsedRealtime()
        requestHeadersBytesCount += request.headers.byteCount()
    }

    override fun requestBodyStart(call: Call) {
        runCatching { targetEventListener?.requestBodyStart(call) }
    }

    override fun requestBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        runCatching { targetEventListener?.requestBodyEnd(call, byteCount) }

        requestEndTimeMs = clock.elapsedRealtime()
        requestBodyBytesSentCount += byteCount
    }

    override fun requestFailed(
        call: Call,
        ioe: IOException,
    ) {
        runCatching { targetEventListener?.requestFailed(call, ioe) }
    }

    override fun responseHeadersStart(call: Call) {
        runCatching { targetEventListener?.responseHeadersStart(call) }

        val requestEndTimeMs = requestEndTimeMs ?: return
        val currentResponseLatencyMs = (clock.elapsedRealtime() - requestEndTimeMs)
        responseLatencyMs = (responseLatencyMs ?: 0) + currentResponseLatencyMs
    }

    override fun responseHeadersEnd(
        call: Call,
        response: Response,
    ) {
        runCatching { targetEventListener?.responseHeadersEnd(call, response) }

        responseHeadersBytesCount += response.headers.byteCount()

        lastResponse = response
    }

    override fun responseBodyStart(call: Call) {
        runCatching { targetEventListener?.responseBodyStart(call) }
    }

    override fun responseBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        runCatching { targetEventListener?.responseBodyEnd(call, byteCount) }

        responseBodyBytesReceivedCount += byteCount
    }

    override fun responseFailed(
        call: Call,
        ioe: IOException,
    ) {
        runCatching { targetEventListener?.responseFailed(call, ioe) }
    }

    override fun callEnd(call: Call) {
        runCatching { targetEventListener?.callEnd(call) }

        val requestInfo = requestInfo ?: return
        val response = lastResponse ?: return

        // If there are multiple requests for a given call `response.request` will be the last of
        // them i.e., redirected request.
        val request = response.request

        val statusCode = response.code

        // Do not use OkHttp's `isSuccess` logic for determining whether a given response is
        // successful or not to keep iOS and Android implementation in sync.
        val isSuccess = (statusCode in 200..<400)

        // Capture response URL attributes in case there was a redirect and attributes such as host,
        // path, and query have different values for the original request and the response.
        // https://square.github.io/okhttp/features/interceptors/#application-interceptors
        val httpResponse =
            HttpResponse(
                host = request.url.host,
                path = HttpUrlPath(request.url.encodedPath),
                query = request.url.query,
                result =
                    if (isSuccess) {
                        HttpResponse.HttpResult.SUCCESS
                    } else {
                        HttpResponse.HttpResult.FAILURE
                    },
                statusCode = statusCode,
                headers = response.headers.toMap(),
            )

        val httpResponseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response = httpResponse,
                durationMs = (clock.elapsedRealtime() - callStartTimeMs),
                metrics = getMetrics(),
            )

        logger?.log(httpResponseInfo)
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        runCatching { targetEventListener?.callFailed(call, ioe) }

        val requestInfo = requestInfo ?: return

        // If there are multiple requests for a given call `response.request` will be the last of
        // them i.e., redirected request.
        val request = lastResponse?.request ?: call.request()

        // Capture response URL attributes in case there was a redirect and attributes such as host,
        // path, and query have different values for the original request and the response.
        // https://square.github.io/okhttp/features/interceptors/#application-interceptors
        val httpResponse =
            HttpResponse(
                host = request.url.host,
                path = HttpUrlPath(request.url.encodedPath),
                query = request.url.query,
                result =
                    if (isInterruptedException(ioe)) {
                        HttpResponse.HttpResult.CANCELED
                    } else {
                        HttpResponse.HttpResult.FAILURE
                    },
                error = ioe,
            )
        val httpResponseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response = httpResponse,
                durationMs = (clock.elapsedRealtime() - callStartTimeMs),
                metrics = getMetrics(),
            )
        logger?.log(httpResponseInfo)
    }

    override fun canceled(call: Call) {
        runCatching { targetEventListener?.canceled(call) }
    }

    override fun satisfactionFailure(
        call: Call,
        response: Response,
    ) {
        runCatching { targetEventListener?.satisfactionFailure(call, response) }
    }

    override fun cacheHit(
        call: Call,
        response: Response,
    ) {
        runCatching { targetEventListener?.cacheHit(call, response) }
    }

    override fun cacheMiss(call: Call) {
        runCatching { targetEventListener?.cacheMiss(call) }
    }

    override fun cacheConditionalHit(
        call: Call,
        cachedResponse: Response,
    ) {
        runCatching { targetEventListener?.cacheConditionalHit(call, cachedResponse) }
    }

    private fun getMetrics(): HttpRequestMetrics =
        HttpRequestMetrics(
            requestBodyBytesSentCount = requestBodyBytesSentCount,
            responseBodyBytesReceivedCount = responseBodyBytesReceivedCount,
            requestHeadersBytesCount = requestHeadersBytesCount,
            responseHeadersBytesCount = responseHeadersBytesCount,
            dnsResolutionDurationMs = dnsResolutionDurationMs,
            tlsDurationMs = tlsDurationMs,
            tcpDurationMs = tcpDurationMs,
            fetchInitializationMs = fetchInitializationMs,
            responseLatencyMs = responseLatencyMs,
        )

    private fun isInterruptedException(e: Throwable): Boolean {
        val cause = (if (e.cause == null) e else e.cause) ?: return false
        return cause::class == InterruptedIOException::class || cause::class == InterruptedException::class
    }

    private fun Long?.validateLength(): Long? {
        // -1 can used to indicate that the length is unknown so we should skip it
        if (this != null && this == -1L) {
            return null
        }
        return this
    }

    private fun Call.shouldIgnoreCurrentPath(): Boolean = request().url.encodedPath == EXCLUDED_BITDRIFT_PATH

    private companion object {
        private const val EXCLUDED_BITDRIFT_PATH = "/bitdrift_public.protobuf.client.v1.ApiService/Mux"
    }
}
