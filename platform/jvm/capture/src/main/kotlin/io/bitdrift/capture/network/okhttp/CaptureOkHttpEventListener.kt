// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import java.io.IOException

internal class CaptureOkHttpEventListener internal constructor(
    private val logger: ILogger?,
    clock: IClock,
    targetEventListener: EventListener?,
) : CaptureOkHttpEventListenerBase(clock, targetEventListener) {

    private var networkSpan: Span? = null

    override fun callStart(call: Call) {
        // Call super to populate requestInfo
        super.callStart(call)
        val request = call.request()

        if (request.header("x-capture-gql-operation-name") != null) {
            val requestFields = buildMap {
                putAll(extraFields(request))
                requestInfo?.let { putAll(it.coreFields) }
            }
            networkSpan = logger?.startSpan("_graphql", LogLevel.DEBUG, requestFields)
        } else {
            requestInfo?.let {
                logger?.log(it)
            }
        }
    }

    override fun callEnd(call: Call) {
        // Call super to populate responseInfo
        super.callEnd(call)
        if (networkSpan != null) {
            networkSpan?.end(SpanResult.SUCCESS, responseInfo?.coreFields)
        } else {
            responseInfo?.let {
                logger?.log(it)
            }
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // Call super to populate responseInfo
        super.callFailed(call, ioe)
        if (networkSpan != null) {
            networkSpan?.end(SpanResult.FAILURE, responseInfo?.coreFields)
        } else {
            responseInfo?.let {
                logger?.log(it)
            }
        }
    }

    private fun extraFields(request: Request): Map<String, String> {
        // TODO(murki): Refactor to be generic and graphql-agnostic
        return buildMap {
            request.header("x-capture-gql-operation-name")?.let {
                put("_operation_name", it)
            }
            request.header("x-capture-gql-operation-id")?.let {
                put("_operation_id", it)
            }
            request.header("x-capture-gql-operation-type")?.let {
                put("_operation_type", it)
            }
        }
    }
}
