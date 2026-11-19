// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import io.bitdrift.capture.replay.internal.ReplayRect
import org.junit.Assert
import org.junit.Test

class ReplayRectTest {
    @Test
    fun testRectToByteEncoding() {
        val expected = byteArrayOf(0.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, 1, 2, 3, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_x_on_2bytes() {
        val expected = byteArrayOf(128.toByte(), 3.toByte(), 232.toByte(), 2.toByte(), 3.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, 1000, 2, 3, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_y_on_2bytes() {
        val expected = byteArrayOf(64.toByte(), 1.toByte(), 3.toByte(), 232.toByte(), 3.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, 1, 1000, 3, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_w_on_2bytes() {
        val expected = byteArrayOf(32.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 232.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, 1, 2, 1000, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_h_on_2bytes() {
        val expected = byteArrayOf(16.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 3.toByte(), 232.toByte())
        val actual = ReplayRect(ReplayType.Label, 1, 2, 3, 1000).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_screenSize() {
        val expected =
            byteArrayOf(
                52.toByte(),
                0.toByte(),
                0.toByte(),
                4.toByte(),
                56.toByte(),
                7.toByte(),
                128.toByte(),
            )
        val actual = ReplayRect(ReplayType.View, 0, 0, 1080, 1920).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_negative_x() {
        val expected = byteArrayOf(128.toByte(), 252.toByte(), 24.toByte(), 2.toByte(), 3.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, -1000, 2, 3, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }

    @Test
    fun testRectToByteEncoding_negative_y() {
        val expected = byteArrayOf(64.toByte(), 1.toByte(), 252.toByte(), 24.toByte(), 3.toByte(), 4.toByte())
        val actual = ReplayRect(ReplayType.Label, 1, -1000, 3, 4).toByteArray()
        Assert.assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            Assert.assertEquals(e, actual[i])
        }
    }
}
