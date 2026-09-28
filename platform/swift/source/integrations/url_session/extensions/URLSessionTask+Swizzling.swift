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
            !URLSessionTaskTracker.supports(task: self) ||
            URLSessionTracePropagation.isURLProtocolRelayRequest(self.originalRequest?.allHTTPHeaderFields)
        {
            return
        }

        // Trace-header injection happens in `CaptureURLProtocol` (`URLProtocol.swift`), which
        // sets `cap_traceContext` on *this* (outer) task once its relay fetch starts -- including
        // for `URLSession.data(for:)`, which bypasses ordinary `URLSession` method swizzling
        // entirely. This used to also attempt injection here as a fallback, via a KVO mutation of
        // `originalRequest` -- confirmed (via a standalone repro) not to affect the actual wire
        // request, and worse, racing with `CaptureURLProtocol.startLoading()`: both generated
        // their own independent trace context for the same task, and whichever ran first got
        // logged as this request's `_trace_id` even though the *other* one was what actually
        // went out on the wire.
        URLSessionTaskTracker.shared.taskWillStart(self)
        try? ObjCWrapper.doTry {
            self.delegate = ProxyURLSessionTaskDelegate(target: self.delegate)
        }
    }
}
