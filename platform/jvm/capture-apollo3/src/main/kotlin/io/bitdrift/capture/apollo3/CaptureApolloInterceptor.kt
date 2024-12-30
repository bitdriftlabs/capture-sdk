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

/**
 * An [ApolloInterceptor] that logs request and response events to the [Capture.Logger].
 */
class CaptureApolloInterceptor: ApolloInterceptor {

    override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
        // Use special header format that is recognized by the CaptureOkHttpEventListener to be transformed into a span
        val requestBuilder = request.newBuilder()
            .addHttpHeader("x-capture-span-key", "gql")
            .addHttpHeader("x-capture-span-gql-name", "graphql")
            .addHttpHeader("x-capture-span-gql-field-operation-id", request.operation.id())
            .addHttpHeader("x-capture-span-gql-field-operation-type", request.operation.type())
            .addHttpHeader("x-capture-span-gql-field-operation-name", request.operation.name())
            .addHttpHeader("x-capture-path-template", request.operation.name()) // set this to override the http _path_template field
        // TODO(murki): Augment request logs with
        //  request.executionContext[CustomScalarAdapters]?.let {
        //    addHttpHeader("x-capture-span-gql-field-operation-variables", request.operation.variables(it).valueMap.toString())
        //  }

        val modifiedRequest = requestBuilder.build()

        // TODO(murki): Augment response logs with response.errors
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
