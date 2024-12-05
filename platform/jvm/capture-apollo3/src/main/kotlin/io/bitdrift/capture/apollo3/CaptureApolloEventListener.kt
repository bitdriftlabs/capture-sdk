// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import android.util.Base64
import android.util.Log
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerBase
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import java.io.IOException

internal class CaptureApolloEventListener internal constructor(
    private val logger: ILogger?,
    clock: IClock,
    targetEventListener: EventListener?,
) : CaptureOkHttpEventListenerBase(clock, targetEventListener) {

    private var graphqlSpan: Span? = null

    override fun callStart(call: Call) {
        // Call super to populate requestInfo
        super.callStart(call)
        val request = call.request()
        Log.d("miguel", "CaptureApolloEventListener - CallStart: Operation header=${request.header(HEADER_GQL_OPERATION_NAME)}")
        // bail if not a gql operation
        val gqlOperationName = request.decodeHeader(HEADER_GQL_OPERATION_NAME) ?: return
        val requestFields = buildMap {
            put("_operation_name", gqlOperationName)
            request.decodeHeader(HEADER_GQL_OPERATION_ID)?.let {
                put("_operation_id", it)
            }
            request.decodeHeader(HEADER_GQL_OPERATION_TYPE)?.let {
                put("_operation_type", it)
            }
            request.decodeHeader(HEADER_GQL_OPERATION_VARIABLES)?.let {
                put("_operation_variables", it)
            }
            requestInfo?.let { putAll(it.coreFields) }
        }
        graphqlSpan = logger?.startSpan("_graphql", LogLevel.DEBUG, requestFields)
    }

    override fun callEnd(call: Call) {
        // Call super to populate responseInfo
        super.callEnd(call)
        // TODO(murki): Figure out how to log graphql-errors failures
        graphqlSpan?.end(SpanResult.SUCCESS, responseInfo?.coreFields)
        Log.d("miguel", "CaptureApolloEventListener - EventListener call operation succeeded.")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // Call super to populate responseInfo
        super.callFailed(call, ioe)
        graphqlSpan?.end(SpanResult.FAILURE, responseInfo?.coreFields)
    }

    private fun Request.decodeHeader(headerName: String): String? {
        return this.headers[headerName]?.let {
            return try {
                String(Base64.decode(it, Base64.NO_WRAP))
            } catch (e: Throwable) {
                Log.w("CaptureGraphQL", "Error decoding internal Capture GraphQL header=$headerName", e)
                null
            }
        }
    }
}
