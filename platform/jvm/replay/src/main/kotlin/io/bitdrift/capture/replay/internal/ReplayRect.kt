// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.replay.ReplayType
import java.io.OutputStream
import kotlin.experimental.or

/**
 * The core Bitdrift Replay data
 * @param type The type of the view
 * @param x The x coordinate of the view
 * @param y The y coordinate of the view
 * @param width The width of the view
 * @param height The height of the view
 */
data class ReplayRect(
    val type: ReplayType,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {

    /**
     * Encodes this object to an OutputStream while reducing the number of bytes used by each Integer.
     * When the integer is smaller than 255 it is encoded on 1 byte, 2 otherwise.
     */
    fun to(outputStream: OutputStream) {
        outputStream.write(toByteArray())
    }

    internal fun toByteArray(): ByteArray {
        var currentIndex = 1
        val array = ByteArray(9)
        array[0] = type.typeValue.toByte()
        for ((index, property) in arrayOf(x, y, width, height).withIndex()) {
            if (property > 255 || property < 0) {
                // The 4 most significant bits of the `type` (1st byte) is used as a mask
                // indicating if [x, y, width, height] spans 2 bytes in this order.
                // Meaning if the most significant bit is 1 then x takes two bytes, then y, etc
                array[0] = array[0] or (1 shl (7 - index)).toByte()

                // property written on 2 bytes
                array[currentIndex] = (property shr 8 and 0xFF).toByte()
                array[currentIndex + 1] = (property and 0xFF).toByte()
                currentIndex += 1
            } else {
                array[currentIndex] = property.toByte()
            }
            currentIndex += 1
        }
        return array.sliceArray(0 until currentIndex)
    }
}
