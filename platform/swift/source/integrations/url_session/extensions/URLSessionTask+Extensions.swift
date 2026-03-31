// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private var kRequestInfoKey: UInt8 = 0
private var kTraceContextKey: UInt8 = 1
private var kHasExistingTraceHeadersKey: UInt8 = 2

extension URLSessionTask {
    /// The HTTP Request Info associated with a given `URLSessionTask`.
    var cap_requestInfo: HTTPRequestInfo? {
        // swiftlint:disable:previous identifier_name
        get { objc_getAssociatedObject(self, &kRequestInfoKey) as? HTTPRequestInfo }
        set {
            objc_setAssociatedObject(self, &kRequestInfoKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
    }

    var cap_traceContext: URLSessionTraceContext? {
        get { objc_getAssociatedObject(self, &kTraceContextKey) as? URLSessionTraceContext }
        set {
            objc_setAssociatedObject(self, &kTraceContextKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
    }

    var cap_hasExistingTraceHeaders: Bool {
        get { (objc_getAssociatedObject(self, &kHasExistingTraceHeadersKey) as? NSNumber)?.boolValue == true }
        set {
            objc_setAssociatedObject(
                self,
                &kHasExistingTraceHeadersKey,
                NSNumber(value: newValue),
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }
}
