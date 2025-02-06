// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.content.Context
import android.util.Base64
import android.util.Log
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.replay.IReplayLogger
import io.bitdrift.capture.replay.ReplayCaptureMetrics
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.internal.FilteredCapture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

object TestUtils {

    fun createReplayPreviewClient(
        replay: AtomicReference<Pair<FilteredCapture, ReplayCaptureMetrics>?>,
        latch: CountDownLatch,
        context: Context,
        backgroundThreadHandler: IBackgroundThreadHandler
    ): ReplayPreviewClient {
        return ReplayPreviewClient(
            object : ErrorHandler {
                override fun handleError(detail: String, e: Throwable?) {
                    Log.e("Replay Tests", "error: $detail $e")
                }
            },
            object : IReplayLogger {
                override fun onScreenCaptured(
                    encodedScreen: ByteArray,
                    screen: FilteredCapture,
                    metrics: ReplayCaptureMetrics
                ) {
                    Log.d("Replay Tests", "took ${metrics.parseDuration.inWholeMilliseconds}ms")
                    Log.d("Replay Tests", "Captured a total of ${screen.size} ReplayRect views.")
                    Log.d("Replay Tests", screen.toString())
                    Log.d(
                        "Replay Tests",
                        "echo \"${
                            Base64.encodeToString(
                                encodedScreen,
                                0
                            )
                        }\" | websocat ws://localhost:3001 --base64 -bv -1"
                    )

                    replay.set(Pair(screen, metrics))
                    latch.countDown()
                }

                override fun logVerboseInternal(message: String, fields: Map<String, String>?) {
                    Log.v("Replay Tests", message)
                }

                override fun logDebugInternal(message: String, fields: Map<String, String>?) {
                    Log.d("Replay Tests", message)
                }

                override fun logErrorInternal(message: String, e: Throwable?, fields: Map<String, String>?) {
                    Log.e("Replay Tests", message, e)
                }
            },
            context,
            backgroundThreadHandler = backgroundThreadHandler
        )
    }
}
