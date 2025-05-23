// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation
import ObjectiveC

extension URLSessionTask {
    @objc
    func cap_resume() {
        defer { self.cap_resume() }
        if self.state == .completed || self.state == .canceling ||
            !URLSessionTaskTracker.supports(task: self)
        {
            return
        }

        URLSessionTaskTracker.shared.taskWillStart(self)
        try? ObjCWrapper.doTry {
            self.delegate = ProxyURLSessionTaskDelegate(target: self.delegate)
        }
    }
}
