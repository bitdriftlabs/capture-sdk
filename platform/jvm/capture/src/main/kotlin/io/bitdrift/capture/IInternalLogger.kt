// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.providers.ArrayFields

/**
 * The Capture SDK logger internal interface.
 */
internal interface IInternalLogger : ILogger {
    // TODO(Fran): BIT-7251 Rename to logInternal
    fun log(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields = ArrayFields.EMPTY,
        matchingArrayFields: ArrayFields = ArrayFields.EMPTY,
        attributesOverrides: LogAttributesOverrides? = null,
        blocking: Boolean = false,
        message: () -> String,
    )
}
