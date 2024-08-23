// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit
import WebKit

extension UIImageView: ReplayUIKitIdentifiable {
    final func identifyUIKit(frame: CGRect) -> AnnotatedView? {
        var imageFrame = frame
        let imageSize = self.image?.size ?? .zero

        switch self.contentMode {
        case .scaleToFill, .scaleAspectFill:
            imageFrame.size = frame.size

        // We need to calculate the gap for an image that is centered with aspect fit and different ratio
        case .scaleAspectFit:
            let imageRatio = imageSize.height > 0 ? imageSize.width / imageSize.height : 0
            let containerRatio = frame.height > 0 ? frame.width / frame.height : 0
            imageFrame.size.width = imageRatio > containerRatio ? frame.width : frame.height * imageRatio
            imageFrame.size.height = imageRatio > containerRatio ? frame.width * imageRatio : frame.height
            imageFrame.origin.x += (frame.size.width / 2.0) - (imageFrame.size.width / 2.0)
            imageFrame.origin.y += (frame.size.height / 2.0) - (imageFrame.size.height / 2.0)
        default:
            imageFrame.size.width = min(imageSize.width, frame.size.width)
            imageFrame.size.height = min(imageSize.height, frame.size.height)
            let alignment = Alignment(contentMode: self.contentMode)
            imageFrame.origin = alignment.applyTo(frame: frame, size: imageFrame.size)
        }

        // Check if the image could be a background image. Any of the following conditions makes the view a
        // background view:
        //
        // - The image view has subviews
        // - The image view intersects with other sibling views
        if let superview = self.superview, let zIndex = superview.subviews.firstIndex(of: self) {
            let relativeFrame = superview.convert(imageFrame, from: nil)
            let neighborsOverlap = superview.subviews[zIndex + 1..<superview.subviews.count]
                .contains { !$0.isHidden && relativeFrame.intersects($0.frame) }

            let isBehindOtherViews = self.subviews.filter { !$0.isHidden }.count > 0 || neighborsOverlap
            return AnnotatedView(isBehindOtherViews ? .backgroundImage : .image, recurse: isBehindOtherViews,
                                 frame: imageFrame)
        }

        return AnnotatedView(.image, frame: imageFrame)
    }
}
