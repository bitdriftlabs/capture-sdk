// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

struct ReplayCommonCategorizer {
    /// This class map is used for the "fast path", just by looking the view class name we can quickly infer
    /// what type it is. Mostly useful for UIKit types.
    static var knownTypes: [String: AnnotatedView] = [
        "UIView": AnnotatedView(.view),
        "_UIBarBackground": AnnotatedView(.backgroundImage, recurse: false),
        "UICollectionView": .skipped,
        "UICollectionViewCell": .skipped,
        "UITextEditor": .ignored,
        "UITextFieldLabel": .ignored,
        "UIDropShadowView": .skipped,
        "UINavigationTransitionView": .skipped,
        "UINavigationItemButtonView": .skipped,
        "UITableViewCell": .skipped,
        "UIInputSetHostView": AnnotatedView(.keyboard, recurse: false),
        "UITableViewCellContentView": .skipped,
        "UITransitionView": .skipped,
        "UIViewControllerWrapperView": .skipped,
        // Used by various SwiftUI controls displaying text (Text, Label).
        "CGDrawingView": AnnotatedView(.label, recurse: false),
        // Used by SwiftUI buttons.
        "_UIShapeHitTestingView": AnnotatedView(.button, recurse: false),
    ]

    /// Infer the type of the view by trying to match the class name or falling back to the
    /// `ReplayIdentifiable` protocol implementation.
    ///
    /// - parameter view:  The view subclass to analyze
    /// - parameter frame: A `CGRect` (using global positioning) that can be modified to adjust the
    ///                    positioning and/or size. Please note this frame is not relative to the superview
    ///
    /// - returns: An annotated view which defines the position and traverse behavior, see
    ///            `AnnotatedViewType` for more information
    static func known(view: UIView, frame: CGRect) -> AnnotatedView? {
        let className = String(describing: type(of: view))
        if var known = self.knownTypes[className] {
            known.frame = frame
            return known
        }

        if let identifiable = view as? ReplayIdentifiable {
            return identifiable.identify(frame: frame)
        }

        if let identifiable = view as? ReplayUIKitIdentifiable {
            return identifiable.identifyUIKit(frame: frame)
        }

        return nil
    }

    /// This tags UIView(s) as Images if the layer content is a CGImage.
    ///
    /// - parameter view:  The view subclass to analyze
    /// - parameter frame: A `CGRect` (using global positioning) that can be modified to adjust the
    ///                    positioning and/or size. Please note this frame is not relative to the superview
    ///
    /// - returns: An annotated view which defines the position and traverse behavior, see
    ///            `AnnotatedViewType` for more information
    static func imageLayer(view: UIView, frame: CGRect) -> AnnotatedView? {
        if CFGetTypeID(view.layer.contents as CFTypeRef) == CGImage.typeID {
            return AnnotatedView(.image, frame: frame)
        }

        return nil
    }
}
