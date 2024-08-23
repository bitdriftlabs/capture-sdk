// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import ObjectiveC

extension URLSessionTask {
    @available(iOS 15.0, *)
    @objc
    func cap_setDelegate(_ delegate: URLSessionTaskDelegate?) {
        // Setting a delegate on a task changes session behavior because it causes session's delegate to
        // stop receiving delegate callbacks. To avoid this, we only set our proxy delegate on a task if
        // a user specifies that the task should have an actual delegate.
        if delegate == nil {
            self.cap_setDelegate(nil)
        } else {
            // The call below doesn't result in an infinite cycle as `cap_setState` was used to replace
            // `setState` so the call below calls the original `setState` implementation.
            self.cap_setDelegate(ProxyURLSessionDelegate(target: delegate))
        }
    }
}
