// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import io.bitdrift.capture.reports.exitinfo.ExitReason
import io.bitdrift.capture.reports.exitinfo.PreviousRunInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

fun assertPreviousRunInfo(
    actual: PreviousRunInfo?,
    hasFatallyTerminated: Boolean,
    terminationReason: ExitReason? = null,
) {
    assertNotNull(actual)
    assertEquals(hasFatallyTerminated, actual?.hasFatallyTerminated)
    assertEquals(terminationReason, actual?.terminationReason)
}
