// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The protocol used to intercept network requests that are about to be started.
final class CaptureURLProtocol: URLProtocol {
    override class func canInit(with task: URLSessionTask) -> Bool {
        URLSessionTaskTracker.shared.taskWillStart(task)
        return false
    }

    override class func canInit(with _: URLRequest) -> Bool {
        return false
    }
}
