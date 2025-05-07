// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.google.gson.annotations.SerializedName
import io.bitdrift.capture.network.okhttp.HttpApiEndpoint
import io.bitdrift.capture.network.okhttp.OkHttpApiClient

internal class DeviceCodeService(
    private val apiClient: OkHttpApiClient,
) {
    fun createTemporaryDeviceCode(
        deviceId: String,
        completion: (CaptureResult<String>) -> Unit,
    ) {
        val typedRequest = DeviceCodeRequest(deviceId)

        apiClient.perform<DeviceCodeRequest, DeviceCodeResponse>(HttpApiEndpoint.GetTemporaryDeviceCode, typedRequest) { result ->
            completion(result.map { it.code })
        }
    }
}

internal data class DeviceCodeRequest(
    @SerializedName("device_id") val deviceId: String,
)

internal data class DeviceCodeResponse(
    @SerializedName("code") val code: String,
)
