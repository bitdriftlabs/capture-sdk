// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.span

/**
 * Represents the end state of the operation represented by a span.
 */
sealed interface SpanResult {
    /**
     * The operation completed successfully.
     */
    data object SUCCESS : SpanResult

    /**
     * The operation failed.
     */
    data object FAILURE : SpanResult

    /**
     * The operation was canceled
     */
    data object CANCELED : SpanResult

    /**
     * The result of the operation is unknown
     */
    data object UNKNOWN : SpanResult
}
