// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.fakes

import android.content.Context
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.IFatalIssueReporter

/**
 * Fake [IFatalIssueReporter]
 */
class FakeFatalIssueReporter : IFatalIssueReporter {
    private var fatalIssueMechanism: FatalIssueMechanism = FatalIssueMechanism.BuiltIn

    override fun initIntegrationMode(appContext: Context) {
        // no-op
    }

    override fun initBuiltInMode(appContext: Context) {
        // no-op
    }

    override fun getReportingMechanism(): FatalIssueMechanism = fatalIssueMechanism

    override fun getLogStatusFieldsMap(): Map<String, FieldValue> = emptyMap()
}
