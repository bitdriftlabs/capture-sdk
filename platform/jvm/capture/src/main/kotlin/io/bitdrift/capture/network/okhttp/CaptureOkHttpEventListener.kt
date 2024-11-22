// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.ILogger
import io.bitdrift.capture.common.IClock
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException

internal class CaptureOkHttpEventListener internal constructor(
    private val logger: ILogger?,
    clock: IClock,
    targetEventListener: EventListener?,
) : CaptureOkHttpEventListenerBase(clock, targetEventListener) {

    override fun callStart(call: Call) {
        super.callStart(call)
        requestInfo?.let {
            logger?.log(it)
        }
    }

    override fun callEnd(call: Call) {
        super.callEnd(call)
        responseInfo?.let {
            logger?.log(it)
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        super.callFailed(call, ioe)
        responseInfo?.let {
            logger?.log(it)
        }
    }
}
