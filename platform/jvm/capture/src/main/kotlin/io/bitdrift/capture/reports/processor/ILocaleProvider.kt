// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

/**
 * Provide a value for device locale
 */
interface ILocaleProvider {
    /**
     * Get the 2-character ISO-3166 country code (or 3-character region code
     * where applicable
     */
    fun getLocaleCode(): String
}
