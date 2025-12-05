// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import android.app.ActivityManager
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.IFatalIssueReporter
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor

class FakeFatalIssueReporter(
    private val initializationState: FatalIssueReporterState,
) : IFatalIssueReporter {
    override fun init(
        activityManager: ActivityManager,
        sdkDirectory: String,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    ) {
        // no-op
    }

    override fun initializationState(): FatalIssueReporterState = initializationState

    override fun getLogStatusFieldsMap(): Map<String, String> = emptyMap()
}
