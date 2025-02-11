// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Application
import android.widget.Toast
import androidx.core.app.NotificationCompat.MessagingStyle.Message
import io.bitdrift.capture.Error
import io.bitdrift.capture.ICaptureStartListener

/**
 * A generic monitor for [Capture.Logger.start()] call
 */
class CaptureStartStatusListener(private val application: Application) : ICaptureStartListener {

    override fun onStartSuccess() {
        showToaster(message = "Capture SDK started successfully")
    }

    override fun onStartFailure(error: Error) {
        showToaster(message = "Capture SDK couldn't start. ${error.message}")
    }

    private fun showToaster(message: String){
        Toast.makeText(application, message, Toast.LENGTH_LONG).show()
    }
}