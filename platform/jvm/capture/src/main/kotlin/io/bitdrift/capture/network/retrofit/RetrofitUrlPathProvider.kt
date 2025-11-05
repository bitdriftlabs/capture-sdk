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
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.HTTP
import retrofit2.http.OPTIONS
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import java.lang.reflect.Method

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
        // TODO(murki): Use annotationUrl() once Retrofit 3.1.0 is released
        //  https://github.com/square/retrofit/pull/4542
        val pathTemplateEntry =
            request
                .tag(Invocation::class.java)
                ?.method()
                ?.parseHttpPath()
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
            Class.forName("retrofit2.Invocation")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun Method.parseHttpPath(): String? {
        for (annotation in annotations) {
            when (annotation) {
                is GET -> return annotation.value
                is HEAD -> return annotation.value
                is POST -> return annotation.value
                is PUT -> return annotation.value
                is PATCH -> return annotation.value
                is DELETE -> return annotation.value
                is OPTIONS -> return annotation.value
                is HTTP -> return annotation.path
            }
        }
        return null
    }

    private fun String.withLeadingSlash() = if (startsWith("/")) this else "/$this"
}
