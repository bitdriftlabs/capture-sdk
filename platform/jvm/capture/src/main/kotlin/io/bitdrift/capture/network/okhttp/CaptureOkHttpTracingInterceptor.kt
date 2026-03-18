// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureRuntimeProvider
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.common.RuntimeStringConfig
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Injects tracing headers into outgoing requests when Capture tracing is active for this session.
 *
 * If the request already contains any known tracing headers (W3C `traceparent`, B3 single `b3`,
 * or B3 multi `X-B3-TraceId`), regardless of the configured propagation format, those headers are
 * left untouched and the trace ID is extracted so it can still be attached to Capture logs.
 *
 * The propagation format is resolved from a runtime config flag.
 */
class CaptureOkHttpTracingInterceptor
    @VisibleForTesting
    internal constructor(
        private val runtimeProvider: IRuntimeProvider,
    ) : Interceptor {
        constructor() : this(CaptureRuntimeProvider)

        private val traceContextFactory by lazy { TraceContextFactory() }

        override fun intercept(chain: Interceptor.Chain): Response {
            val currentLogger = Capture.logger()

            val request = chain.request()
            if (currentLogger == null) {
                // This means the SDK wasn't initialized, so this interceptor should be a no-op
                return chain.proceed(request)
            }

            val propagationMode = getPropagationMode()
            if (!shouldAddTraceHeaders(currentLogger, propagationMode, request)) {
                return chain.proceed(request)
            }

            val existingTraceId = extractExistingTraceId(request)
            if (existingTraceId != null) {
                val requestBuilder = request.newBuilder()
                requestBuilder.header(TRACE_ID_HEADER, existingTraceId)
                return chain.proceed(requestBuilder.build())
            }

            val requestBuilder = request.newBuilder()

            val traceContext = traceContextFactory.generateTraceContext()
            when (propagationMode) {
                TracePropagationMode.W3C -> {
                    requestBuilder.header("traceparent", "00-${traceContext.traceId}-${traceContext.spanId}-01")
                }

                TracePropagationMode.B3_SINGLE -> {
                    requestBuilder.header("b3", "${traceContext.traceId}-${traceContext.spanId}-1")
                }

                TracePropagationMode.B3_MULTI -> {
                    requestBuilder.header("X-B3-TraceId", traceContext.traceId)
                    requestBuilder.header("X-B3-SpanId", traceContext.spanId)
                    requestBuilder.header("X-B3-Sampled", "1")
                }

                TracePropagationMode.NONE -> return chain.proceed(request)
            }
            requestBuilder.header(TRACE_ID_HEADER, traceContext.traceId)
            return chain.proceed(requestBuilder.build())
        }

        private fun extractExistingTraceId(request: Request): String? {
            // W3C traceparent: 00-<traceId>-<spanId>-<flags>
            val w3c = request.header("traceparent")?.split("-")?.getOrNull(1)
            if (w3c != null) return w3c

            // B3 single: <traceId>-<spanId>-<sampled>[-<parentSpanId>]
            val b3Single = request.header("b3")?.split("-")?.getOrNull(0)
            if (b3Single != null) return b3Single

            // B3 multi
            return request.header("X-B3-TraceId")
        }

        private fun shouldAddTraceHeaders(
            currentLogger: ILogger,
            propagationMode: TracePropagationMode,
            request: Request,
        ): Boolean =
            currentLogger.isTracingActive && propagationMode != TracePropagationMode.NONE && request.header("x-bitdrift-api-key") == null

        private fun getPropagationMode(): TracePropagationMode {
            val runtimeValue =
                runtimeProvider.getRuntimeStringConfigValue(RuntimeStringConfig.TRACE_PROPAGATION_MODE)
            return TracePropagationMode.fromRuntimeValue(runtimeValue)
        }

        internal companion object {
            internal const val TRACE_ID_HEADER = "x-capture-span-trace-id"
        }
    }
