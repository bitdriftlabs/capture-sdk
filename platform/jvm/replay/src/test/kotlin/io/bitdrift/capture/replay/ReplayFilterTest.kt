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

    // Filter will aggregate the different window roots
    @Test
    fun testFilter_mergeList() {
        val listOfRects = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val listOfRects2 = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val capture = listOf(listOfRects, listOfRects2)
        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(filteredCapture!!.size, 6)
    }

    // Filter out ignored ReplayRect
    @Test
    fun testFilter_removeIgnored() {
        val listOfRects = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.Ignore, 0, 20, 30, 40), // Filtered out
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val listOfRects2 = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val capture = listOf(listOfRects, listOfRects2)
        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(filteredCapture!!.size, 6)
    }

    // Sending twice the same list of replay rect through the filter will filter the last one
    @Test
    fun testFilter_filterIdentical() {
        val listOfRects = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val listOfRects2 = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val capture = listOf(listOfRects, listOfRects2)
        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(filteredCapture!!.size, 6)

        val nextCapture = replayFilter.filter(capture)
        Assert.assertNull(nextCapture)
    }

    @Test
    fun testFilter_filterIdenticalWithIgnore() {
        val listOfRects = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.Ignore, 0, 20, 30, 40), // Filtered out
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
        )

        val listOfRects2 = listOf(
            ReplayRect(ReplayType.Label, 1, 2, 3, 4),
            ReplayRect(ReplayType.Button, 0, 20, 30, 40),
            ReplayRect(ReplayType.View, 0, 20, 30, 40),
            ReplayRect(ReplayType.Ignore, 0, 20, 30, 40), // Filtered out
            ReplayRect(ReplayType.Ignore, 0, 20, 30, 40), // Filtered out
        )

        val capture = listOf(listOfRects, listOfRects2)
        val replayFilter = ReplayFilter()

        val filteredCapture = replayFilter.filter(capture)
        Assert.assertNotNull(filteredCapture)
        Assert.assertEquals(filteredCapture!!.size, 6)

        val nextCapture = replayFilter.filter(capture)
        Assert.assertNull(nextCapture)
    }
}
