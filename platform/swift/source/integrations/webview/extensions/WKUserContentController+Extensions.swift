// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import WebKit

private var kIsInstrumentedKey: UInt8 = 0

extension WKUserContentController {
    var cap_isInstrumented: Bool {
        get { (objc_getAssociatedObject(self, &kIsInstrumentedKey) as? NSNumber)?.boolValue == true }
        set {
            objc_setAssociatedObject(
                self,
                &kIsInstrumentedKey,
                NSNumber(value: newValue),
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }
}
