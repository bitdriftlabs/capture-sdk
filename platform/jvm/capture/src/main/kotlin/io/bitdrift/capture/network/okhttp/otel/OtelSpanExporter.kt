// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

import com.google.gson.Gson
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.network.okhttp.InternalTelemetryRequestTag
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Exports OTel spans for traced network requests to a user-configured OTLP/HTTP endpoint.
 *
 * Reuses the SDK's shared [OkHttpClient] and OkHttp's own async dispatcher (`enqueue`), the same
 * pattern already used by `OkHttpCaptureApiClient` for authenticated POSTs to bitdrift's own API
 * — no new executor or thread pool needed. Export is best-effort: failures are reported to the
 * error handler but never surfaced to the host app or retried, since a dropped span must never
 * affect the traced request itself.
 */
internal class OtelSpanExporter(
    private val configuration: OtelExportConfiguration,
    private val client: OkHttpClient,
    private val errorHandler: ErrorHandler,
    private val gson: Gson = Gson(),
) {
    fun export(resource: Resource, span: Span) {
        val payload =
            ResourceSpansPayload(
                resourceSpans =
                    listOf(
                        ResourceSpans(
                            resource = resource,
                            scopeSpans =
                                listOf(
                                    ScopeSpans(
                                        scope = Scope(name = SCOPE_NAME),
                                        spans = listOf(span),
                                    ),
                                ),
                        ),
                    ),
            )

        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request =
            Request
                .Builder()
                .url(configuration.endpoint)
                .header(configuration.authHeaderName, configuration.authHeaderValue)
                .header("Content-Type", "application/json")
                .tag(InternalTelemetryRequestTag::class.java, InternalTelemetryRequestTag)
                .post(body)
                .build()

        client
            .newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.close()
                    }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        errorHandler.handleError("Failed to export OTel span", e)
                    }
                },
            )
    }

    private companion object {
        private const val SCOPE_NAME = "bitdrift-capture"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
