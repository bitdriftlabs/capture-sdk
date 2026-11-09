// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

enum Alignment {
    case top
    case topLeft
    case topRight
    case center
    case left
    case right
    case bottom
    case bottomLeft
    case bottomRight

    init(contentMode: UIView.ContentMode) {
        switch contentMode {
        case .center, .scaleToFill, .scaleAspectFit, .scaleAspectFill, .redraw:
            self = .center
        case .top:
            self = .top
        case .bottom:
            self = .bottom
        case .left:
            self = .left
        case .right:
            self = .right
        case .topLeft:
            self = .topLeft
        case .topRight:
            self = .topRight
        case .bottomLeft:
            self = .bottomLeft
        case .bottomRight:
            self = .bottomRight

        @unknown default:
            self = .center
        }
    }

    // swiftlint:disable:next cyclomatic_complexity
    init(textAlignment: NSTextAlignment, vertical: UIControl.ContentVerticalAlignment) {
        let layoutDirection = UIView.userInterfaceLayoutDirection(for: .unspecified)

        switch (textAlignment, vertical) {
        case (.left, .top), (.justified, .top):
            self = .topLeft
        case (.left, .bottom), (.justified, .bottom):
            self = .bottomLeft
        case (.left, .center), (.justified, .center):
            self = .left
        case (.right, .top):
            self = .topRight
        case (.right, .bottom):
            self = .bottomRight
        case (.right, .center):
            self = .right
        case (.center, .top):
            self = .top
        case (.center, .bottom):
            self = .bottom
        case (.center, .center):
            self = .center

        case (.natural, .top):
            self = layoutDirection == .leftToRight ? .topLeft : .topRight
        case (.natural, .bottom):
            self = layoutDirection == .leftToRight ? .bottomLeft : .bottomRight
        case (.natural, .center):
            self = layoutDirection == .leftToRight ? .left : .right

        case (_, .fill):
            self = .topLeft

        @unknown default:
            self = .topLeft
        }
    }

    func applyTo(frame: CGRect, size: CGSize) -> CGPoint {
        let viewFrame = frame
        func rightCentered() -> CGPoint {
            CGPoint(
                x: viewFrame.origin.x + max(0, viewFrame.size.width - size.width),
                y: viewFrame.origin.y + max(0, (viewFrame.size.height / 2.0) - (size.height / 2.0))
            )
        }

        func bottomCentered() -> CGPoint {
            CGPoint(
                x: viewFrame.origin.x + max(0, (viewFrame.size.width / 2.0) - (size.width / 2.0)),
                y: viewFrame.origin.y + max(0, viewFrame.size.height - size.height)
            )
        }

        func centered() -> CGPoint {
            CGPoint(
                x: viewFrame.origin.x + max(0, (viewFrame.size.width / 2.0) - (size.width / 2.0)),
                y: viewFrame.origin.y + max(0, (viewFrame.size.height / 2.0) - (size.height / 2.0))
            )
        }

        switch self {
        case .top:
            return CGPoint(x: centered().x, y: frame.origin.y)
        case .topRight:
            return CGPoint(x: rightCentered().x, y: frame.origin.y)
        case .center:
            return centered()
        case .left:
            return CGPoint(x: frame.origin.x, y: centered().y)
        case .right:
            return rightCentered()
        case .bottom:
            return bottomCentered()
        case .bottomLeft:
            return CGPoint(x: frame.origin.x, y: bottomCentered().y)
        case .bottomRight:
            return CGPoint(x: rightCentered().x, y: bottomCentered().y)
        case .topLeft:
            return frame.origin
        }
    }
}
