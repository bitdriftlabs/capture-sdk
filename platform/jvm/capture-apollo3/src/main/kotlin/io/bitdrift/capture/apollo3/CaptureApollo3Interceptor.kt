// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.SpanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * An [ApolloInterceptor] that logs request and response events to the [Capture.Logger].
 */
class CaptureApollo3Interceptor internal constructor(private val internalLogger: ILogger?) : ApolloInterceptor {

    constructor() : this(Capture.logger())

    // attempts to get the latest logger if one wasn't found at construction time
    private val logger: ILogger?
        get() = internalLogger ?: Capture.logger()

    override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
        val requestFields = buildMap {
            put("_operation_type", request.operation.type())
            put("_operation_name", request.operation.name())
            request.scalarAdapters?.let {
                put("_operation_variables", request.operation.variables(it).valueMap.toString())
            }
        }
        val graphqlSpan = logger?.startSpan("_graphql", LogLevel.DEBUG, requestFields) // use "reserved" magic string

        return chain.proceed(request).onEach { response ->
            if (!response.hasErrors()) {
                graphqlSpan?.end(SpanResult.SUCCESS)
            } else {
                graphqlSpan?.end(
                    SpanResult.FAILURE,
                    mapOf("_graphql_errors" to (response.errors?.joinToString("|") ?: "none"))
                )
            }
        }
    }

    private fun <D : Operation.Data> Operation<D>.type(): String {
        return when (this) {
            is Query        -> "query"
            is Mutation     -> "mutation"
            is Subscription -> "subscription"
            else            -> this.javaClass.simpleName
        }
    }

    private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
        get() = executionContext[CustomScalarAdapters]
}
