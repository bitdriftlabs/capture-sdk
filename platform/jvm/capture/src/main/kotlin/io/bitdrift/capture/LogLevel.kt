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
    ERROR(4),
    WARNING(3),
    INFO(2),
    DEBUG(1),
    TRACE(0),
}
