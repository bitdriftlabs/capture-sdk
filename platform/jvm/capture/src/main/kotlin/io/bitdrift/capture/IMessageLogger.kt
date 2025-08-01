// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * A lightweight logging interface designed for efficient lambda usage.
 *
 * Implementers should define how to handle log messages with a deferred message block.
 * This interface supports lambda inlining optimizations to avoid allocations when logging is disabled.
 */
fun interface IMessageLogger {
    /**
     * Logs a message at a specified level.
     *
     * @param level the severity of the log.
     * @param fields and optional collection of key-value pairs to be added to the log line.
     * @param throwable an optional throwable to include in the log line.
     * @param message the main message of the log line, the lambda gets evaluated lazily.
     */
    fun log(
        level: LogLevel,
        fields: Map<String, String>?,
        throwable: Throwable?,
        message: () -> String,
    )
}

/**
 * Inline extension for [IMessageLogger] that defers lambda allocation until necessary.
 *
 * Use this method to log messages efficiently when the logger might not be active.
 *
 * @param level the severity level of the log.
 * @param fields optional map of additional context fields.
 * @param throwable optional throwable to include.
 * @param message the log message, evaluated lazily.
 */
inline fun IMessageLogger.logInline(
    level: LogLevel,
    fields: Map<String, String>? = null,
    throwable: Throwable? = null,
    crossinline message: () -> String,
) {
    log(level, fields, throwable) { message() }
}
