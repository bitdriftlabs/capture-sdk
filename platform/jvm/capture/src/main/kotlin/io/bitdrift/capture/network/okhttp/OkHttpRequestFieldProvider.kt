package io.bitdrift.capture.network.okhttp

import okhttp3.Request

/**
 * Provides additional custom fields to add to http logs automatically sent
 * by using [CaptureOkHttpEventListenerFactory].
 */
fun interface OkHttpRequestFieldProvider {
    /**
     * @return a map of fields to add to the http log that will be sent
     * by [CaptureOkHttpEventListenerFactory] for this [request].
     */
    fun provideExtraFields(request: Request): Map<String, String>
}