package io.bitdrift.capture.apollo3

import android.util.Log
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain

/**
 * An [HttpInterceptor] that complements the [CaptureApolloInterceptor] in order to instrument graphql operations.
 */
class CaptureApolloHttpInterceptor: HttpInterceptor {
    override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
        val modifiedRequest = removeCaptureInternalHeaders(request)
        // TODO(murki): Enrich log with ApolloHttpException error info
        return chain.proceed(modifiedRequest)
    }

    private fun removeCaptureInternalHeaders(request: HttpRequest): HttpRequest {
        Log.d("miguel", "CaptureApolloHttpInterceptor - Original header count=${request.headers.size}")
        val cleanedHeaders = request.headers.filterNot {
            it.name.equals(HEADER_GQL_OPERATION_NAME, true) ||
                    it.name.equals(HEADER_GQL_OPERATION_ID, true) ||
                    it.name.equals(HEADER_GQL_OPERATION_TYPE, true) ||
                    it.name.equals(HEADER_GQL_OPERATION_VARIABLES, true)
        }
        Log.d("miguel", "CaptureApolloHttpInterceptor - Cleaned header count=${cleanedHeaders.size}")

        val requestBuilder = request.newBuilder().apply {
            headers(cleanedHeaders)
        }

        return requestBuilder.build()
    }
}