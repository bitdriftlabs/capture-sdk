// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

extension UIButton: ReplayUIKitIdentifiable {
    final func identifyUIKit(frame: CGRect) -> AnnotatedView? {
        let hasText = !(self.titleLabel?.text ?? "").isEmpty
        let hasImage = self.imageView?.image != nil
        return AnnotatedView(.button, frame: frame, ignoreWhenEmpty: !hasText && !hasImage)
    }
}
