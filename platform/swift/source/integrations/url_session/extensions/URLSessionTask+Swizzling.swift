// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import ObjectiveC
@_implementationOnly import CaptureLoggerBridge

extension URLSessionTask {
    @available(iOS 15.0, *)
    @objc
    func cap_resume() {
        defer { self.cap_resume() }
        if (self.state == .completed || self.state == .canceling) {
            return
        }

        URLSessionTaskTracker.shared.taskWillStart(self)
        try? ObjCTry.do {
            self.delegate = ProxyURLSessionTaskDelegate(target: self.delegate)
        }
    }
}
