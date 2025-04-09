// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import kotlin.time.Duration

/**
 * Holds the latest [FatalIssueReporter] status
 */
data class FatalIssueReporterStatus(
    /**
     * The state of the reporting
     */
    val state: FatalIssueReporterState,
    /**
     * Total duration for init the reporting
     */
    val duration: Duration? = null,
    /**
     * The mechanism selected
     */
    val mechanism: FatalIssueMechanism,
)
