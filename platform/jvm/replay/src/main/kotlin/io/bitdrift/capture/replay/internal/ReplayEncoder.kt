// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import java.io.ByteArrayOutputStream

// Encode a screen capture to an output stream
internal class ReplayEncoder {
    fun encode(scanResult: FilteredCapture): ByteArray {
        val outputStream = ByteArrayOutputStream()
        outputStream.use { output ->
            for (view in scanResult) {
                view.to(output)
            }
        }
        return outputStream.toByteArray()
    }
}
