// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.network.okhttp.HttpApiEndpoint
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class DeviceCodeService(
    private val apiClient: OkHttpApiClient,
) {
    fun createTemporaryDeviceCode(
        deviceId: String,
        completion: (CaptureResult<String>) -> Unit,
    ) {
        val typedRequest = DeviceCodeRequest(deviceId)

        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(
            HttpApiEndpoint.GetTemporaryDeviceCode,
            typedRequest,
        ) { result ->
            val mappedResult =
                when (result) {
                    is CaptureResult.Success -> CaptureResult.Success(result.value.code)
                    is CaptureResult.Failure -> CaptureResult.Failure(result.error)
                }
            completion(mappedResult)
        }
    }
}

@Serializable
internal data class DeviceCodeRequest(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
internal data class DeviceCodeResponse(
    @SerialName("code") val code: String,
)
