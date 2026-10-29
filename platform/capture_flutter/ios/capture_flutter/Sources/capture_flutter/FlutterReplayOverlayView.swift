// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import UIKit

/// Transparent view installed inside the `FlutterView` that exposes the wireframe computed by the Dart
/// widget tree walker to the native session replay traversal.
final class FlutterReplayOverlayView: UIView, ReplayIdentifiable {
    private static let flutterViewClassName = "FlutterView"

    /// Rects in the overlay's coordinate space (Flutter logical pixels).
    private var rects: [(frame: CGRect, type: ViewType)] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        self.isUserInteractionEnabled = false
        self.backgroundColor = .clear
        self.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        self.accessibilityElementsHidden = true
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func update(rects: [(frame: CGRect, type: ViewType)]) {
        self.rects = rects
    }

    func identify(frame: CGRect) -> AnnotatedView? {
        let fragments = self.rects.map { rect in
            (rect.frame.offsetBy(dx: frame.minX, dy: frame.minY), rect.type)
        }
        return AnnotatedView(
            .transparentView,
            recurse: false,
            frame: frame,
            fragments: fragments,
            ignoreWhenEmpty: false
        )
    }

    /// Returns the overlay attached to the first visible `FlutterView`, installing it if needed.
    static func attached(in windows: [UIWindow]) -> FlutterReplayOverlayView? {
        guard let flutterView = windows.lazy.compactMap({ self.findFlutterView(in: $0) }).first else {
            return nil
        }

        if let existing = flutterView.subviews.lazy.compactMap({ $0 as? FlutterReplayOverlayView }).first {
            return existing
        }

        let overlay = FlutterReplayOverlayView(frame: flutterView.bounds)
        flutterView.insertSubview(overlay, at: 0)
        return overlay
    }

    private static func findFlutterView(in view: UIView) -> UIView? {
        if NSStringFromClass(type(of: view)) == self.flutterViewClassName {
            return view
        }

        for subview in view.subviews {
            if let found = self.findFlutterView(in: subview) {
                return found
            }
        }

        return nil
    }
}
