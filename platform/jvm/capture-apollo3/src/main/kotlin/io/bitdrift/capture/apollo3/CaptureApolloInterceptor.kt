// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.bitdrift.capture.Capture
import kotlinx.coroutines.flow.Flow

internal const val HEADER_GQL_OPERATION_NAME = "x-capture-gql-operation-name"
internal const val HEADER_GQL_OPERATION_ID = "x-capture-gql-operation-id"
internal const val HEADER_GQL_OPERATION_TYPE = "x-capture-gql-operation-type"

/**
 * An [ApolloInterceptor] that logs request and response events to the [Capture.Logger].
 */
class CaptureApolloInterceptor: ApolloInterceptor {

    override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
        val requestBuilder = request.newBuilder()
            .addHttpHeader(HEADER_GQL_OPERATION_NAME, request.operation.name())
            .addHttpHeader(HEADER_GQL_OPERATION_ID, request.operation.id())
            .addHttpHeader(HEADER_GQL_OPERATION_TYPE, request.operation.type())

        val modifiedRequest = requestBuilder.build()

        // TODO(murki): Enrich response logs with response.errors
        return chain.proceed(modifiedRequest)
    }

    private fun <D : Operation.Data> Operation<D>.type(): String {
        return when (this) {
            is Query        -> "query"
            is Mutation     -> "mutation"
            is Subscription -> "subscription"
            else            -> this.javaClass.simpleName
        }
    }
}
