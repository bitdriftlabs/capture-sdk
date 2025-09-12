// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

import java.io.InputStream

/**
 * Process reports via streaming values
 */
interface IStreamingReportProcessor {
    /**
     * Call to convert a trace input stream into a report file
     */
    fun reportANR(
        stream: InputStream,
        destination: String,
        manufacturer: String,
        model: String,
        osVersion: String,
        osBrand: String,
        appId: String,
        appVersion: String,
        versionCode: Long,
    )
}
