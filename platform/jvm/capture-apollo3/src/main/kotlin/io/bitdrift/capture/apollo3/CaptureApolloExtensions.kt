package io.bitdrift.capture.apollo3

import com.apollographql.apollo3.ApolloClient

/**
 * Adds the companion [CaptureApolloInterceptor] and [CaptureApolloHttpInterceptor] to the [ApolloClient.Builder].
 */
fun ApolloClient.Builder.captureAutoInstrument(): ApolloClient.Builder {
    addInterceptor(CaptureApolloInterceptor())
    addHttpInterceptor(CaptureApolloHttpInterceptor())
    return this
}