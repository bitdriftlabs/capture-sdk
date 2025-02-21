// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private var kRequestInfoKey: UInt8 = 0

extension URLSessionTask {
    /// The HTTP Request Info associated with a given `URLSessionTask`.
    var cap_requestInfo: HTTPRequestInfo? {
        // swiftlint:disable:previous identifier_name
        get { objc_getAssociatedObject(self, &kRequestInfoKey) as? HTTPRequestInfo }
        set {
            objc_setAssociatedObject(self, &kRequestInfoKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
    }
}
