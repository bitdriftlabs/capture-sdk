// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.apollo3

import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import okhttp3.Call
import okhttp3.EventListener

/**
 * Emits Capture logs for Apollo GraphQL OkHttp network traffic.
 *
 * Usage - set instance of [CaptureGraphQLEventListenerFactory] on [okhttp3.OkHttpClient.Builder.eventListener]
 * using [okhttp3.OkHttpClient.Builder.eventListenerFactory] method in conjunction with [CaptureGraphQLInterceptor] inside
 * the [com.apollographql.apollo3.ApolloClient.Builder].
 *
 * val apolloClient = ApolloClient.Builder()
 *    .okHttpClient(OkHttpClient.Builder()
 *       .eventListenerFactory(CaptureGraphQLEventListenerFactory())
 *       .build())
 *    .addInterceptor(CaptureApollo3Interceptor())
 *    .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
 *    .build()
 *
 * [CaptureGraphQLEventListenerFactory] has multiple constructors that allow passing an instance of
 * [okhttp3.EventListener] or [okhttp3.EventListener.Factory] if you already use
 * [okhttp3.OkHttpClient.Builder.eventListener].
 */
class CaptureGraphQLEventListenerFactory internal constructor(
    private val targetEventListenerCreator: ((call: Call) -> EventListener)? = null,
    private val logger: ILogger? = Capture.logger(),
    private val clock: IClock = DefaultClock.getInstance(),
) : EventListener.Factory {
    /**
     * Initializes a new instance of the Capture event listener.
     */
    constructor() : this(null)

    /**
     * Initializes a new instance of the Capture event listener. Accepts an instance of an existing event
     * listener to enable combining the Capture event listener with other existing listeners.
     *
     * @param targetEventListener The existing event listener that should be informed about
     * [okhttp3.OkHttpClient] events alongside the Capture event listeners.
     */
    constructor(targetEventListener: EventListener) : this({ targetEventListener })

    /**
     * Initializes a new instance of the Capture event listener. Accepts an instance of an existing event
     * listener to make it possible to combine the Capture event listener with other existing listeners.
     *
     * @param targetEventListenerFactory The existing event listener factory that should be used to create event listeners
     * to be informed about [okhttp3.OkHttpClient] events alongside the Capture event listener.
     */
    constructor(targetEventListenerFactory: EventListener.Factory) : this(
        targetEventListenerCreator = { targetEventListenerFactory.create(it) },
    )

    override fun create(call: Call): EventListener {
        return CaptureGraphQLEventListener(getLogger(), clock, targetEventListenerCreator?.invoke(call))
    }

    // attempts to get the latest logger if one wasn't found at construction time
    private fun getLogger(): ILogger? {
        return logger ?: Capture.logger()
    }
}