// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import com.google.gson.annotations.SerializedName

/**
 * Represents the model where will store Crash/ANR/etc
 *
 * TODO(FranAguilera): BIT-5070 Update to include full FatalIssueReport
 */
internal data class FatalIssueReport(
    @SerializedName("issue_type")
    val issueType: String,
    val errors: List<Exception>,
)

internal data class Exception(
    val name: String,
    val reason: String,
)
