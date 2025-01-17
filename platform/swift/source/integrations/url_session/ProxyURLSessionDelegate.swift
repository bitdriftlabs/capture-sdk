// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private let kFinishedCollectingMetrics
    = #selector(URLSessionTaskDelegate.urlSession(_:task:didFinishCollecting:))

/// This proxy should be use exclusively in URLSession delegates, do not use this in URLSessionTasks, for that use
/// `ProxyURLSessionTaskDelegate` instead, which calls the session delegate callbacks too.
@objc(CAPProxyURLSessionDelegate)
class ProxyURLSessionDelegate: NSObject {
    /// `URLSession`'s `delegate` property is a `strong` reference. It's uncommon for delegates on iOS to be
    /// `strong` references but to make our delegate mimic the behavior of `URLSession`'s delegate we hold
    /// a strong reference to the underlying delegate.
    fileprivate let target: URLSessionTaskDelegate?

    init(target: URLSessionTaskDelegate?) {
        self.target = target
    }

    override func forwardingTarget(for _: Selector!) -> Any? {
        return self.target
    }

    override func responds(to aSelector: Selector!) -> Bool {
        return self.target?.responds(to: aSelector) ?? super.responds(to: aSelector)
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
        self.target?.urlSession?(session, task: task, didFinishCollecting: metrics)
    }
}

@objc(CAPProxyURLSessionTaskDelegate)
final class ProxyURLSessionTaskDelegate: ProxyURLSessionDelegate {
    override func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didFinishCollecting metrics: URLSessionTaskMetrics
    )
    {
        URLSessionTaskTracker.shared.task(task, didFinishCollecting: metrics)
        
        if let delegate = self.target, delegate.responds(to: kFinishedCollectingMetrics) {
            delegate.urlSession?(session, task: task, didFinishCollecting: metrics)
            return
        }

        // We need to call the session delegate method because otherwise by attaching this proxy
        // delegate to a task delegate and conforming to this method, we'll prevent the networking
        // framewokr to call URLSession's delegate method and therefore changing the behavior of the
        // stack.
        if let sessionDelegate = session.delegate as? URLSessionTaskDelegate,
            !sessionDelegate.isKind(of: ProxyURLSessionDelegate.self)
        {
            sessionDelegate.urlSession?(session, task: task, didFinishCollecting: metrics)
        }
    }
}
