// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.timber

import android.util.Log
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import timber.log.Timber

/**
 * Capture's implementation of a [Timber.Tree]. It forwards all the logs to [Capture.Logger]
 * if the Logger is initialized.
 */
open class CaptureTree internal constructor(
    private val internalLogger: ILogger?,
) : Timber.Tree() {
    constructor() : this(Capture.logger())

    // attempts to get the latest logger if one wasn't found at construction time
    private val logger: ILogger?
        get() = internalLogger ?: Capture.logger()

    final override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        logger?.log(toCaptureLogLevel(priority), extractFields(tag), t) { message }
    }

    private fun toCaptureLogLevel(priority: Int): LogLevel =
        when (priority) {
            Log.VERBOSE -> LogLevel.TRACE
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARNING
            Log.ERROR -> LogLevel.ERROR
            else -> LogLevel.DEBUG // default level
        }

    private fun extractFields(tag: String?): Map<String, String> =
        buildMap {
            put("source", "Timber")
            tag?.let { put("tag", it) }
        }
}
