// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.Field
import kotlin.time.Duration

/**
 * The Capture SDK logger internal interface.
 */
internal interface IInternalLogger : ILogger {
    fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields = ArrayFields.EMPTY,
        matchingArrayFields: ArrayFields = ArrayFields.EMPTY,
        attributesOverrides: LogAttributesOverrides? = null,
        blocking: Boolean = false,
        message: () -> String,
    )

    fun logInternal(
        type: LogType,
        level: LogLevel,
        arrayFields: ArrayFields,
        throwable: Throwable?,
        message: () -> String,
    )

    fun reportInternalError(
        detail: String,
        throwable: Throwable? = null,
    )

    fun flush(blocking: Boolean)

    fun logResourceUtilization(
        arrayFields: ArrayFields,
        duration: Duration,
    )

    fun logSessionReplayScreenshot(
        fields: Array<Field>,
        duration: Duration,
    )

    fun logSessionReplayScreen(
        fields: Array<Field>,
        duration: Duration,
    )
}
