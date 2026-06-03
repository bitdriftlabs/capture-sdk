// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import io.bitdrift.capture.replay.internal.ReplayFilter
import io.bitdrift.capture.replay.internal.ReplayRect
import org.junit.Assert
import org.junit.Test

class ReplayFilterTest {
    @Test
    fun testFilter_firstCapture() {
        val capture =
            listOf(
                ReplayRect(ReplayType.Label, 1, 2, 3, 4),
                ReplayRect(ReplayType.Button, 0, 20, 30, 40),
                ReplayRect(ReplayType.View, 0, 20, 30, 40),
            )

        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(3, filteredCapture!!.size)
    }

    // Sending twice the same list of replay rect through the filter will filter the last one
    @Test
    fun testFilter_filterIdentical() {
        val capture =
            listOf(
                ReplayRect(ReplayType.Label, 1, 2, 3, 4),
                ReplayRect(ReplayType.Button, 0, 20, 30, 40),
                ReplayRect(ReplayType.View, 0, 20, 30, 40),
            )

        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(3, filteredCapture!!.size)

        val nextCapture = replayFilter.filter(capture)
        Assert.assertNull(nextCapture)
    }

    @Test
    fun testFilter_filterEmpty() {
        val capture = emptyList<ReplayRect>()
        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNull(filteredCapture)
    }
}
