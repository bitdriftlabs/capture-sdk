// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

/// UILabel's positions need to be adjusted according to its text and alignment. We try to calculate the
/// actual text position and size (as opposed to the hosting UILabel) to visualize more accurately what
/// the user saw (short words vs long words, etc)
extension UILabel: ReplayUIKitIdentifiable {
    final func identifyUIKit(frame: CGRect) -> AnnotatedView? {
        guard let text = self.attributedText, text.length > 0 else {
            return .ignored
        }

        var frame = frame
        frame.size = text.boundingRect(with: self.frame.size, context: nil).size

        switch self.textAlignment {
        case .center:
            frame.origin.x += ((self.frame.size.width / 2) - (frame.size.width / 2)).rounded()
        case .right:
            frame.origin.x += self.frame.size.width - frame.size.width
        default:
            break
        }

        frame.origin.y += ((self.frame.size.height / 2) - (frame.size.height / 2)).rounded()
        return AnnotatedView(.label, recurse: false, frame: frame)
    }
}
