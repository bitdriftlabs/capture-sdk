// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import io.bitdrift.capture.providers.DateProvider
import java.util.Date

/**
 * Fake [DateProvider] for use in tests.
 * Returns a fixed timestamp by default, but can be configured to return different values.
 */
object FakeDateProvider : DateProvider {
    const val DEFAULT_TEST_TIMESTAMP: Long = 1736942400000L

    override fun invoke(): Date = Date(DEFAULT_TEST_TIMESTAMP)
}
