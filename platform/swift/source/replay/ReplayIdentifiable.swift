// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// This protocol is public and is the way consumers can expand our identification logic. Making any
/// UIView subclass implement `ReplayIdentifiable` will ensure that when it's found in the
/// view hierarchy, the resulting representation will be of the returned type.
///
/// NOTE: It's very likely you don't need to do this if you inherit from a UIKit type, since Bitdrift is
/// already trying to infer the right type / positioning for these cases.
///
/// Example:
///
/// ```swift
/// extension MyLabel: ReplayIdentifiable {
///   func identify(frame: inout CGRect) -> (type: ViewType, recurse: Bool)? { return (.label, false) }
/// }
/// ```
public protocol ReplayIdentifiable where Self: UIView {
    /// A function that returns the desired type for the receiver. This method can optionally re-defined
    /// the final frame. This is useful when the visual content does not match the receiver frame.
    /// For example, an ImageView with an image that is centered can set the frame for the image itself
    /// as opposed to the frame of the hosting view.
    ///
    /// - parameter frame: A `CGRect` using global positioning (ie this frame is not relative to its
    ///                    superview).
    ///
    /// - returns: An annotated view which defines the position and traverse behavior, see
    ///            `AnnotatedViewType` for more information.
    func identify(frame: CGRect) -> AnnotatedView?
}

/// Internal protocol to provide UIKit identifications for known subclasses. This is split from
/// `ReplayIdentifiable` mainly because we want consumers with subclasses of `UIKit` types to be able to
/// define their types independently of our heuristic.
protocol ReplayUIKitIdentifiable where Self: UIView {
    /// This method works the same way as `ReplayIdentifiable.identify` refer to that for more information.
    ///
    /// - parameter frame: See `ReplayIdentifiable.identify`.
    ///
    /// - returns: See `ReplayIdentifiable.identify`.
    func identifyUIKit(frame: CGRect) -> AnnotatedView?
}
