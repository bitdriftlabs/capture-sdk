// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Formats view's frame as a compact binary array, as follows:
///
/// type    1 byte*
/// x       1 or 2 bytes
/// y       1 or 2 bytes
/// width   1 or 2 bytes
/// height  1 or 2 bytes
///
/// The 4 most significant bits of the `type` (1st byte) is used as a mask indicating if [x, y, width, height]
/// spans 2 bytes in this order. Meaning if the most significant bit is 1 then x takes two bytes, then y, etc
///
/// - parameter type:   The type of the view being represented (will be allocated in the least significant
///                     bits
///                     of the first byte
/// - parameter buffer: The buffer where the binary array will be stored
/// - parameter frame:  The frame of the view being represented in screen coordinates
@inline(__always)
func rectToBytes(type: ViewType, buffer: inout Data, frame: CGRect) {
    var currentIndex = 1
    var array = [UInt8](repeating: 0, count: 9)
    array[0] = type.rawValue
    for (index, property) in [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height]
        .enumerated()
    {
        let roundedProperty = Int(property.rounded())
        if roundedProperty > 255 || roundedProperty < 0 {
            array[0] |= 1 << (7 - index)
            array[currentIndex] = UInt8((roundedProperty >> 8) & 0xFF)
            array[currentIndex + 1] = UInt8(roundedProperty & 0xFF)
            currentIndex += 1
        } else {
            array[currentIndex] = UInt8(roundedProperty)
        }

        currentIndex += 1
    }

    buffer.append(array, count: currentIndex)
}
