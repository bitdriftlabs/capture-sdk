// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.error

import android.util.Log
import com.google.gson.annotations.SerializedName
import io.bitdrift.capture.ApiError
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.network.okhttp.HttpApiEndpoint
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import io.bitdrift.capture.providers.FieldProvider

internal class ErrorReporterService(
    private val fieldProviders: List<FieldProvider>,
    private val apiClient: OkHttpApiClient,
) : IErrorReporter {
    private fun headers(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        for (fieldProvider in fieldProviders) {
            for ((key, value) in fieldProvider()) {
                val updatedKey = "x-" + key.replace("_", "-")
                map[updatedKey] = value
            }
        }

        return map
    }

    @Suppress("UnsafePutAllUsage")
    override fun reportError(
        message: String,
        details: String?,
        fields: Map<String, String>,
    ) {
        val typedRequest = ErrorReportRequest(message, details)

        val allFields =
            buildMap {
                putAll(headers())
                putAll(fields)
            }

        apiClient.perform<ErrorReportRequest, Unit>(
            HttpApiEndpoint.ReportSdkError,
            typedRequest,
            allFields,
        ) { result ->
            when (result) {
                is CaptureResult.Success -> {
                    Log.i("capture", "Successfully reported error to bitdrift service")
                }

                is CaptureResult.Failure ->
                    when (val error = result.error) {
                        is ApiError.ServerError -> {
                            Log.w(
                                "capture",
                                "Failed to report error to bitdrift service, got ${error.statusCode} response",
                            )
                        }

                        else -> {
                            Log.e("capture", "Failed to report error to bitdrift service: ${error.message}")
                        }
                    }
            }
        }
    }
}

internal data class ErrorReportRequest(
    @SerializedName("message") val message: String,
    @SerializedName("details") val details: String?,
)
