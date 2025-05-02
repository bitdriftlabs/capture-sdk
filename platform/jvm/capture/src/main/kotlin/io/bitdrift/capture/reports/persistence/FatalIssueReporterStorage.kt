// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import com.google.gson.Gson
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import java.io.File

/**
 * Persists a [FatalIssueReport] into disk
 */
internal class FatalIssueReporterStorage(
    private val destinationDirectory: File,
) : IFatalIssueReporterStorage {
    // TODO(FranAguilera): BIT-5083 Replace storing via Gson with native call
    private val gson by lazy { Gson() }

    override fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        fatalIssueType: FatalIssueType,
        fatalIssueReport: FatalIssueReport,
    ) {
        // TODO(FranAguilera): BIT-5083 Replace storing via Gson with native call
        val fileName = "${terminationTimeStampInMilli}_${fatalIssueType.name}.json"
        val outputFile = File(destinationDirectory, fileName)
        outputFile.writeText(gson.toJson(fatalIssueReport))
        CaptureJniLibrary.processFatalIssue()
    }
}
