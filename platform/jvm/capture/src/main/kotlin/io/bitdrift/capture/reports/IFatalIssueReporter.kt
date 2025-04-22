// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.content.Context
import io.bitdrift.capture.providers.FieldValue

/**
 * Handles internal reporting of crashes
 */
interface IFatalIssueReporter {
    /**
     * Initializes the reporter
     */
    fun initialize(
        appContext: Context,
        fatalIssueMechanism: FatalIssueMechanism,
    )

    /**
     * Generates the [InternalFieldsMap] to be reported upon Capture.Logger.start with
     * details of FatalIssueReporter state
     */
    fun getLogStatusFieldsMap(): Map<String, FieldValue>
}
