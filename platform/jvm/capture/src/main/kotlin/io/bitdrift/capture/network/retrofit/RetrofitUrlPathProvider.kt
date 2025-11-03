// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.retrofit

import io.bitdrift.capture.network.HttpField
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import okhttp3.Request
import retrofit2.Invocation

/**
 * Automatically extracts url path fields from Retrofit definitions
 * by using [io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListener].
 * @param chainedProvider an optional provider to chain with this one.
 */
class RetrofitUrlPathProvider(
    private val chainedProvider: OkHttpRequestFieldProvider? = null,
) : OkHttpRequestFieldProvider {
    /**
     * @return a map of fields to add to the http log that will be sent
     * by [io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListener] for this [request].
     */
    override fun provideExtraFields(request: Request): Map<String, String> {
        val chainedFields = chainedProvider?.provideExtraFields(request) ?: emptyMap()
        if (!isRetrofitAvailable) {
            return chainedFields
        }
        val pathTemplateEntry =
            request
                .tag(Invocation::class.java)
                ?.annotationUrl()
                ?.let { HttpField.PATH_TEMPLATE to it.withLeadingSlash() }

        return if (pathTemplateEntry != null) {
            chainedFields + pathTemplateEntry
        } else {
            chainedFields
        }
    }

    /** Reflectively tries to determine if the required Retrofit version is on the classpath. */
    private val isRetrofitAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            // annotationUrl() was added in https://github.com/square/retrofit/pull/4542
            // so we need to check for it's existence before we try to use it.
            val invocationClass = Class.forName("retrofit2.Invocation")
            invocationClass.getMethod("annotationUrl")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    private fun String.withLeadingSlash() = if (startsWith("/")) this else "/$this"
}
