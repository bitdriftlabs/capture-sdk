// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.util.Log
import com.google.gson.annotations.SerializedName
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.network.okhttp.HttpApiEndpoint
import io.bitdrift.capture.network.okhttp.OkHttpApiClient

internal interface IOpaqueUserIdService {
    fun registerOpaqueUserId(
        opaqueUserId: String,
        deviceId: String,
    )
}

internal class OpaqueUserIdService(
    private val apiClient: OkHttpApiClient,
    private val clock: IClock = DefaultClock.getInstance(),
) : IOpaqueUserIdService {
    private val maxCallsPerMinute = 5
    private val intervalMs = 60_000L
    private val callTimestampsMs = ArrayDeque<Long>()
    private var lastKnownNowMs: Long = 0

    override fun registerOpaqueUserId(
        opaqueUserId: String,
        deviceId: String,
    ) {

        // TODO: Fran. Probably should move this logic to its own interceptor
        if (!shouldSend()) {
            Log.w(Capture.LOG_TAG, "Skipping opaque user id registration because rate limit is exceeded")
            return
        }

        val typedRequest = OpaqueUserIdRequest(opaqueUserId, deviceId)

        apiClient.perform<OpaqueUserIdRequest, Unit>(
            HttpApiEndpoint.RegisterOpaqueUserId,
            typedRequest,
        ) { result ->
            when (result) {
                is CaptureResult.Success -> {
                    // TODO: Fran. To decide if callback is needed to notify customer success registration
                    Log.i(Capture.LOG_TAG, "Successfully registered opaque user id")
                }

                is CaptureResult.Failure -> {
                    // TODO: Fran. To decide internal error/ or use error handler
                    Log.w(Capture.LOG_TAG, "Failed to register opaque user id: ${result.error.message}")
                }
            }
        }
    }

    @Synchronized
    private fun shouldSend(): Boolean {
        val now = maxOf(clock.elapsedRealtime(), lastKnownNowMs)
        lastKnownNowMs = now
        val cutoff = now - intervalMs

        callTimestampsMs.removeAll { timestampMs -> timestampMs <= cutoff }

        if (callTimestampsMs.size >= maxCallsPerMinute) {
            return false
        }

        callTimestampsMs.addLast(now)
        return true
    }
}

internal data class OpaqueUserIdRequest(
    @SerializedName("opaque_user_id") val opaqueUserId: String,
    @SerializedName("device_id") val deviceId: String,
)
