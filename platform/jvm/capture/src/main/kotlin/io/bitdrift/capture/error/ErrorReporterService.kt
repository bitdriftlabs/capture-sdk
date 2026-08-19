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
import io.bitdrift.capture.network.okhttp.OkHttpCaptureApiClient

internal class ErrorReporterService(
    private val apiClient: Lazy<OkHttpCaptureApiClient>,
) : IErrorReporter {
    override fun reportError(
        message: String,
        details: String?,
        fields: Map<String, String>,
    ) {
        val typedRequest = ErrorReportRequest(message, details)

        apiClient.value.perform<ErrorReportRequest, Unit>(
            HttpApiEndpoint.ReportSdkError,
            typedRequest,
            fields,
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
