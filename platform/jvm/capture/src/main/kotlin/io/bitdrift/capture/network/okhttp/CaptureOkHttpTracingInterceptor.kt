// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.ILogger
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects tracing headers into outgoing requests when Capture tracing is active for this session.
 */
class CaptureOkHttpTracingInterceptor
    @JvmOverloads
    constructor(
        private val logger: ILogger?,
        private val headerFormat: HeaderFormat = HeaderFormat.W3C,
    ) : Interceptor {
        enum class HeaderFormat {
            W3C,
            B3,
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val currentLogger = logger
            if (currentLogger == null || !currentLogger.isTracingActive) {
                return chain.proceed(request)
            }

            val trace = CaptureTracing.newTraceContext()
            val requestBuilder = request.newBuilder()

            when (headerFormat) {
                HeaderFormat.W3C -> {
                    requestBuilder.header("traceparent", trace.traceparent)
                }
                HeaderFormat.B3 -> {
                    requestBuilder.header("b3", trace.b3)
                }
            }

            requestBuilder.header("x-capture-span-key", "trace")
            requestBuilder.header("x-capture-span-trace-field-trace_id", trace.traceId)

            return chain.proceed(requestBuilder.build())
        }
    }
