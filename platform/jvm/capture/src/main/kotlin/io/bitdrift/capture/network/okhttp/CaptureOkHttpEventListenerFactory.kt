// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import okhttp3.Call
import okhttp3.EventListener

/**
 * Emits Capture logs for OkHttp network traffic.
 *
 * Usage - set instance of [CaptureOkHttpEventListenerFactory] on [okhttp3.OkHttpClient.Builder.eventListener]
 * using [okhttp3.OkHttpClient.Builder.eventListenerFactory] method.
 *
 * val client = OkHttpClient.Builder()
 *    .eventListenerFactory(CaptureOkHttpEventListenerFactory())
 *    .build()
 *
 * [CaptureOkHttpEventListenerFactory] has multiple constructors that allow passing an instance of
 * [okhttp3.EventListener] or [okhttp3.EventListener.Factory] if you already use
 * [okhttp3.OkHttpClient.Builder.eventListener].
 */
class CaptureOkHttpEventListenerFactory internal constructor(
    private val targetEventListenerCreator: ((call: Call) -> EventListener)? = null,
    private val logger: ILogger? = Capture.logger(),
    private val clock: IClock = DefaultClock.getInstance(),
    private val requestFieldProvider: OkHttpRequestFieldProvider = DEFAULT_REQUEST_FIELD_PROVIDER,
    private val responseFieldProvider: OkHttpResponseFieldProvider = DEFAULT_RESPONSE_FIELD_PROVIDER,
) : EventListener.Factory {
    /**
     * Initializes a new instance of the Capture event listener.
     *
     * @param requestFieldProvider Provider for request-based extra fields.
     * @param responseFieldProvider Provider for response-based extra fields.
     */
    constructor(
        requestFieldProvider: OkHttpRequestFieldProvider = DEFAULT_REQUEST_FIELD_PROVIDER,
        responseFieldProvider: OkHttpResponseFieldProvider = DEFAULT_RESPONSE_FIELD_PROVIDER,
    ) : this(
        targetEventListenerCreator = null,
        requestFieldProvider = requestFieldProvider,
        responseFieldProvider = responseFieldProvider,
    )

    /**
     * Initializes a new instance of the Capture event listener. Accepts an instance of an existing event
     * listener to enable combining the Capture event listener with other existing listeners.
     *
     * @param targetEventListener The existing event listener that should be informed about
     * [okhttp3.OkHttpClient] events alongside the Capture event listeners.
     * @param requestFieldProvider Provider for request-based extra fields.
     * @param responseFieldProvider Provider for response-based extra fields.
     */
    constructor(
        targetEventListener: EventListener,
        requestFieldProvider: OkHttpRequestFieldProvider = DEFAULT_REQUEST_FIELD_PROVIDER,
        responseFieldProvider: OkHttpResponseFieldProvider = DEFAULT_RESPONSE_FIELD_PROVIDER,
    ) : this(
        targetEventListenerCreator = { targetEventListener },
        requestFieldProvider = requestFieldProvider,
        responseFieldProvider = responseFieldProvider,
    )

    /**
     * Initializes a new instance of the Capture event listener with an existing event listener factory.
     *
     * @param targetEventListenerFactory Factory for creating event listeners alongside Capture.
     * @param requestFieldProvider Provider for request-based extra fields.
     * @param responseFieldProvider Provider for response-based extra fields.
     */
    constructor(
        targetEventListenerFactory: EventListener.Factory,
        requestFieldProvider: OkHttpRequestFieldProvider = DEFAULT_REQUEST_FIELD_PROVIDER,
        responseFieldProvider: OkHttpResponseFieldProvider = DEFAULT_RESPONSE_FIELD_PROVIDER,
    ) : this(
        targetEventListenerCreator = { targetEventListenerFactory.create(it) },
        requestFieldProvider = requestFieldProvider,
        responseFieldProvider = responseFieldProvider,
    )

    override fun create(call: Call): EventListener {
        val currentLogger = getLogger()
        val targetEventListener = targetEventListenerCreator?.invoke(call)
        if (currentLogger == null) {
            return targetEventListener ?: EventListener.NONE
        }
        return CaptureOkHttpEventListener(
            logger = currentLogger,
            clock = clock,
            targetEventListener = targetEventListener,
            requestExtraFieldsProvider = requestFieldProvider,
            responseExtraFieldsProvider = responseFieldProvider,
        )
    }

    // attempts to get the latest logger if one wasn't found at construction time
    private fun getLogger(): ILogger? = logger ?: Capture.logger()

    private companion object {
        private val DEFAULT_REQUEST_FIELD_PROVIDER = OkHttpRequestFieldProvider { emptyMap() }
        private val DEFAULT_RESPONSE_FIELD_PROVIDER = OkHttpResponseFieldProvider { _ -> emptyMap() }
    }
}
