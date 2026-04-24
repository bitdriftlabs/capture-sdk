// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.providers.fieldsOfOptional

internal class NativeWebChromeClient(
    private val originalClient: WebChromeClient?,
    private val logger: IInternalLogger,
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level =
            when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARNING
                ConsoleMessage.MessageLevel.LOG -> LogLevel.DEBUG
                ConsoleMessage.MessageLevel.TIP -> LogLevel.INFO
                ConsoleMessage.MessageLevel.DEBUG -> LogLevel.DEBUG
                else -> LogLevel.DEBUG
            }

        val fields =
            fieldsOfOptional(
                "_level" to consoleMessage.messageLevel().name.lowercase(),
                "_message" to consoleMessage.message(),
                "_source" to "webview",
                "_mode" to "native",
                "_source_id" to consoleMessage.sourceId(),
                "_line_number" to consoleMessage.lineNumber().toString(),
            )

        logger.log(level, fields) {
            "webview.console"
        }

        return originalClient?.onConsoleMessage(consoleMessage) ?: false
    }
}
