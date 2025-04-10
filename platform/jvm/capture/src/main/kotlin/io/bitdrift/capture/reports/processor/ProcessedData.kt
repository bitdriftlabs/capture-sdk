// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.reports.ErrorDetails
import io.bitdrift.capture.reports.ThreadDetails

/**
 * Holds the most relevant data extracted from a JVM/AppExit report to be added into
 * [io.bitdrift.capture.reports.FatalIssueReport]
 */
internal data class ProcessedData(
    val errors: List<ErrorDetails>,
    val threadDetails: ThreadDetails,
)
