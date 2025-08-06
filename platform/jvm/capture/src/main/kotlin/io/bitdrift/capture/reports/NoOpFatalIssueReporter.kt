// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.content.Context
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.reports.processor.IJniFatalIssueProcessor

/**
 * A NoOp instance provided when crash reporting is not enabled.
 *
 * This will report as [FatalIssueMechanism.None]
 */
internal class NoOpFatalIssueReporter : IFatalIssueReporter {
    override fun initBuiltInMode(
        appContext: Context,
        clientAttributes: IClientAttributes,
        jniProcessor: IJniFatalIssueProcessor,
    ) {
        // no-op
    }

    override fun getReportingMechanism(): FatalIssueMechanism = FatalIssueMechanism.None

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> = emptyMap()
}
