package io.bitdrift.capture.network.retrofit

import io.bitdrift.capture.network.HttpField
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
        val fields =
            request
                .tag(Invocation::class.java)
                ?.annotationUrl()
                ?.let { mapOf(HttpField.PATH_TEMPLATE to it.withLeadingSlash()) }
                ?: emptyMap()
        return fields
    }

    /** Reflectively tries to determine if the required Retrofit version is on the classpath. */
    private val isRetrofitAvailable by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            // annotationUrl() was added in Retrofit 2.10.0 so we need to check for it's existence
            // before we try to use it.
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
