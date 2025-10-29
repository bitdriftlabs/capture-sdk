package io.bitdrift.capture.network.okhttp

import okhttp3.Request

/**
 * Reads the request path templates from [headerName] headers and returns them as a
 * comma separate list.
 */
class HeaderBasedOkHttpRequestPathTemplateProvider(
  private val headerName: String = "x-capture-path-template"
) : OkHttpRequestPathTemplateProvider {
  override fun providePathTemplate(request: Request): String? {
    val pathTemplateHeaderValues = request.headers.values(headerName)
    return if (pathTemplateHeaderValues.isEmpty()) {
        null
      } else {
        pathTemplateHeaderValues.joinToString(",")
      }
  }
}
