// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.error

/**
 * Used to report a single unexpected report.
 */
interface IErrorReporter {
    /**
     * Reports an unexpected error.
     * @param message the error message to the report.
     * @param details the details to attach to the report.
     * @param fields the fields to attach to the report.
     */
    fun reportError(
        message: String,
        details: String?,
        fields: Map<String, String>,
    )
}
