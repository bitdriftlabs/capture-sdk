// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

private typealias CategorizerFunction = (UIView, CGRect) -> AnnotatedView?
private let kIgnoredWindows = Set(["UIRemoteKeyboardWindow"])

/// Main replay logic. This class can traverse UIWindow(s) as well as serialize view informations into a
/// byte array.
package final class Replay {
    /// The last known rendering time, expressed in seconds.
    private(set) var renderTime: CFAbsoluteTime = 0

    private let categorizers: [CategorizerFunction] = [
        ReplayCommonCategorizer.known,
        ReplayCommonCategorizer.imageLayer,
    ]

    package init() {}

    /// This method allows consumers to provide a type for a given class name. This is useful when protocol
    /// extension is not possible, but also provides a very quick inference path.
    ///
    /// - parameter knownClass: A string representing an exact match of the class name
    ///                         (e.g. your UIView subclass).
    ///
    /// - parameter type:       The annotated view which defines the position and traverse behavior,
    ///                         see `AnnotatedViewType` for more information.
    func add(knownClass: String, type: AnnotatedView) {
        ReplayCommonCategorizer.knownTypes[knownClass] = type
    }

    /// Capture the current screen by traversing all visible views
    ///
    /// - returns: The bytes array representing the view hierarchy in our binary format. See `rectToBytes` for
    ///            more information about the bytes structure.
    package func capture() -> Data {
        let startTime = CFAbsoluteTimeGetCurrent()
        var buffer = Data()
        rectToBytes(type: .view, buffer: &buffer, frame: UIScreen.main.bounds)

        for window in UIApplication.shared.sessionReplayWindows() {
            let windowClass = NSStringFromClass(type(of: window))
            if window.isHidden || window.alpha < 0.1 || kIgnoredWindows.contains(windowClass) {
                continue
            }

            self.traverse(into: &buffer, parent: window, parentPosition: .zero, clipTo: window.frame)
        }

        self.renderTime = CFAbsoluteTimeGetCurrent() - startTime
        return buffer
    }

    // MARK: - Private methods

    private func layerHasVisibleContent(_ layer: CALayer) -> Bool {
        let layerIsVisible = !layer.isHidden && layer.opacity > 0.1
        return layerIsVisible && (
            // Layer contents might hold to bitmaps so lets just assume is visible
            layer.contents != nil ||

                // Check the background color alpha (note UIColor.clear has alpha=0)
                (layer.backgroundColor?.alpha ?? 0.0) > 0.1 ||

                // Is it a shape and the background is visible?
                ((layer as? CAShapeLayer)?.fillColor?.alpha ?? 0.0) > 0.1 ||

                // Find a visible layer in the whole layer hierarchy
                layer.sublayers?
                .contains { $0.delegate == nil && self.layerHasVisibleContent($0) } == true
        )
    }

    private func traverse(into buffer: inout Data, parent: UIView, parentPosition: CGPoint, clipTo: CGRect,
                          ignoreViewType: Bool = false)
    {
        for view in parent.subviews {
            if view.isHidden || view.alpha < 0.1 {
                continue
            }

            // Set the origin coordinates as absolute
            var frame = view.frame
            frame.origin.x += parentPosition.x
            frame.origin.y += parentPosition.y

            let childClipTo = view.clipsToBounds ? frame.intersection(clipTo) : clipTo
            var aType = AnnotatedView(.view, frame: frame)
            for categorizer in self.categorizers {
                if let categorized = categorizer(view, frame) {
                    aType = categorized
                    break
                }
            }

            let hasVisibleContent = self.layerHasVisibleContent(view.layer)

            // We want to ignore views recognized as plain 'view' if the parent has a known type. This is in
            // order to reduce visual artifacts from inner views on buttons, switches, etc.
            let isIgnored = (aType.type == .view && ignoreViewType)
                || aType.type == .ignore
                || (!hasVisibleContent && aType.ignoreWhenEmpty)

            if !isIgnored, aType.type == .view,
               let alpha = view.layer.backgroundColor?.alpha,
               alpha < 0.8
            {
                aType = AnnotatedView(.transparentView, frame: aType.frame)
            }

            let clippedFrame = aType.frame.intersection(clipTo)
            if !isIgnored, !clippedFrame.isEmpty, clippedFrame.intersects(clipTo) {
                rectToBytes(type: aType.type, buffer: &buffer, frame: clippedFrame)

                for fragment in aType.fragments {
                    if !fragment.frame.intersects(clipTo) {
                        continue
                    }

                    rectToBytes(type: fragment.type, buffer: &buffer,
                                frame: fragment.frame.intersection(clipTo))
                }
            }

            if aType.recurse {
                frame.origin.x -= view.bounds.minX
                frame.origin.y -= view.bounds.minY
                self.traverse(into: &buffer, parent: view, parentPosition: frame.origin, clipTo: childClipTo,
                              ignoreViewType: ignoreViewType || aType.ignoreChildrenViews)
            }
        }
    }
}

extension UIApplication {
    func sessionReplayWindows() -> [UIWindow] {
        self.connectedScenes.flatMap { ($0 as? UIWindowScene)?.windows ?? [] }
    }
}
