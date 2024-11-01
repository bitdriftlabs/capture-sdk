// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Defines the view types used to represent rectangles visually when rendering the session replay.
///
/// Note that as it's currently implemented, we only have 4 bits available and therefore we cannot exceed
/// 15 types.
///
/// Types left: 3
enum ViewType: UInt8 {
    case label = 0
    case button = 1
    case textInput = 2
    case image = 3
    case view = 4
    case backgroundImage = 5
    case switchOn = 6
    case switchOff = 7
    case map = 8
    case chevron = 9
    case transparentView = 10
    case keyboard = 11
    case webview = 12

    case ignore = 254
}

/// A view type annotated with some behavioral settings to define how view traversal is performed.
public struct AnnotatedView {
    /// The type of the view (eg. view, label, etc)
    let type: ViewType

    /// A boolean indicating if the traversal should stop (false) or continue with its subviews (true).
    let recurse: Bool

    /// Determines if the view should be treated as `.ignore` if its content is empty.
    let ignoreWhenEmpty: Bool

    /// Determines if while traversing, we should ignore all the children that are inferred as `.view`
    let ignoreChildrenViews: Bool

    /// Defines the frame containing the absolute position and size of the represented view
    var frame: CGRect

    /// Optional array that allows to add specific fragments into the view representation, for example a
    /// UITextField can add a fragment to represent the entered text.
    let fragments: [(frame: CGRect, type: ViewType)]

    init(_ type: ViewType, recurse: Bool = true, frame: CGRect = .zero,
         fragments: [(CGRect, ViewType)] = [], ignoreWhenEmpty: Bool? = nil,
         ignoreChildrenViews: Bool? = nil)
    {
        self.ignoreWhenEmpty = ignoreWhenEmpty ?? (recurse || type == .ignore)
        self.ignoreChildrenViews = ignoreChildrenViews ??
            (type != .view && type != .ignore && type != .transparentView)

        self.type = type
        self.recurse = recurse
        self.fragments = fragments
        self.frame = frame
    }

    /// Shortcuts to allow for easy definitions, note all these shortcuts default to frame = .zero
    static let skipped = AnnotatedView(.ignore)
    static let ignored = AnnotatedView(.ignore, recurse: false)
}
