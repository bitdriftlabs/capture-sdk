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
 */
internal data class FatalIssueReport(
    @SerializedName("issue_type")
    val issueType: String,
    val errors: List<Exception>,
    val threads: ThreadMetrics
)

internal data class StackTraceElementData(
    @SerializedName("symbol_name")
    val symbolName: String,
    @SerializedName("source_file")
    val sourceFile: SourceFile
)

internal data class SourceFile(
    val path: String,
    val line: Int
)

internal data class Exception(
    val name: String,
    val reason: String,
    @SerializedName("stack_trace")
    val stackTrace: List<StackTraceElementData>
)

internal data class ThreadData(
    val name: String,
    val active: Boolean,
    val index: Int,
    val state: String,
    @SerializedName("stack_trace")
    val stackTrace: List<StackTraceElementData>
)

internal data class ThreadMetrics(
    val count: Int,
    val threads: List<ThreadData>
)
