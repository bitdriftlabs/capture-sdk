// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * A level associated with a log. A higher log level typically indicates that the log is more severe.
 *
 * The log level can be used to filter logs at a coarse granularity.
 *
 * @property value the numeric representation of the log level.
 */
enum class LogLevel(val value: Int) {
    /**
     * Represents an error log level, which is the most severe log level.
     */
    ERROR(4),

    /**
     * Represents a warning log level, which is less severe than error.
     */
    WARNING(3),

    /**
     * Represents an info log level, which is less verbose than debug.
     */
    INFO(2),

    /**
     * Represents a debug log level, which is more verbose than info.
     */
    DEBUG(1),

    /**
     * Represents a trace log level, which is the most verbose log level.
     */
    TRACE(0),
}
