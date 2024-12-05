// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import android.util.Base64
import android.util.Log
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.http.get
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.bitdrift.capture.Capture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

internal const val HEADER_GQL_OPERATION_NAME = "x-capture-gql-operation-name"
internal const val HEADER_GQL_OPERATION_ID = "x-capture-gql-operation-id"
internal const val HEADER_GQL_OPERATION_TYPE = "x-capture-gql-operation-type"
internal const val HEADER_GQL_OPERATION_VARIABLES = "x-capture-gql-operation-variables"

/**
 * An [ApolloInterceptor] that logs request and response events to the [Capture.Logger].
 */
class CaptureApolloInterceptor: ApolloInterceptor {

    @OptIn(ApolloExperimental::class)
    override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
        val requestBuilder = request.newBuilder()
            .addHttpHeader(HEADER_GQL_OPERATION_NAME, request.operation.name().encodeBase64())
            .addHttpHeader(HEADER_GQL_OPERATION_ID, request.operation.id().encodeBase64())
            .addHttpHeader(HEADER_GQL_OPERATION_TYPE, request.operation.type().encodeBase64())
        request.scalarAdapters?.let {
            requestBuilder.addHttpHeader(HEADER_GQL_OPERATION_VARIABLES, request.operation.variables(it).valueMap.toString().encodeBase64())
        }

        val modifiedRequest = requestBuilder.build()

        Log.d("miguel", "CaptureApolloInterceptor - intercept: Operation header=${modifiedRequest.httpHeaders?.get(HEADER_GQL_OPERATION_NAME)}")

        return chain.proceed(modifiedRequest).onEach { response ->
            if (!response.hasErrors()) {
                Log.d("miguel", "CaptureApolloInterceptor - Graphql operation ${request.operation.name()} Succeeded.")
            } else {
                // TODO(murki): Put errors in headers and pass along
                Log.d("miguel-apollo3", "Graphql operation ${request.operation.name()} failed. Errors=${response.errors?.joinToString("|") ?: "none"}")
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

    private fun String.encodeBase64(): String {
        return Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)
    }
}
