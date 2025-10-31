package io.bitdrift.capture.network.retrofit

import io.bitdrift.capture.network.HttpFieldKey
import io.bitdrift.capture.network.okhttp.OkHttpRequestFieldProvider
import okhttp3.Request
import retrofit2.Invocation

/**
 * Automatically extracts url path fields from Retrofit definitions
 * by using [io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListener].
 */
class RetrofitUrlPathProvider : OkHttpRequestFieldProvider {

    /**
     * @return a map of fields to add to the http log that will be sent
     * by [io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListener] for this [request].
     */
    override fun provideExtraFields(request: Request): Map<String, String> {
        if (!isRetrofitAvailable) {
            return emptyMap()
        }
        val fields = request.tag(Invocation::class.java)
            ?.annotationUrl()
            ?.let { mapOf(HttpFieldKey.PATH_TEMPLATE to it.withLeadingSlash()) }
            ?: emptyMap()
        return fields
    }

    /** Reflectively tries to determine if Retrofit is on the classpath. */
    private val isRetrofitAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            Class.forName("retrofit2.Invocation")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun String.withLeadingSlash() = if (startsWith("/")) this else "/$this"
}