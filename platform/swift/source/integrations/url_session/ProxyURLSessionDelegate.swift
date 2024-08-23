// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private let kFinishedCollectingMetrics
    = #selector(URLSessionTaskDelegate.urlSession(_:task:didFinishCollecting:))

@objc(CAPProxyURLSessionDelegate)
final class ProxyURLSessionDelegate: NSObject {
    /// `URLSession`'s `delegate` property is a `strong` reference. It's uncommon for delegates on iOS to be
    /// `strong` references but to make our delegate mimic the behavior of `URLSession`'s delegate we hold
    /// a strong reference to the underlying delegate.
    private let target: URLSessionTaskDelegate?

    init(target: URLSessionTaskDelegate?) {
        self.target = target
    }

    override func forwardingTarget(for _: Selector!) -> Any? {
        return self.target
    }

    override func responds(to aSelector: Selector!) -> Bool {
        if kFinishedCollectingMetrics == aSelector {
            return true
        }

        return self.target?.responds(to: aSelector) ?? false
    }
}

extension ProxyURLSessionDelegate: URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didFinishCollecting metrics: URLSessionTaskMetrics
    )
    {
        URLSessionTaskTracker.shared.task(task, didFinishCollecting: metrics)
        if self.target?.responds(to: kFinishedCollectingMetrics) ?? false {
            self.target?.urlSession?(session, task: task, didFinishCollecting: metrics)
        }
    }
}
