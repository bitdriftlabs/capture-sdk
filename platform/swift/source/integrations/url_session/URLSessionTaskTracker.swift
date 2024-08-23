// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Responsible for logging request and response logs for tracked `URLSessionTask`'s. Works for all but
/// `WebSocket` and `Stream` tasks.
///
/// The tracker makes a best effort to access `URLSessionTaskTransactionMetrics` for a given task, but
/// that's not always possible. Specifically, the tracker doesn't have access to
/// `URLSessionTaskTransactionMetrics` for tasks started using `URLSession` instances created prior
/// to the start of the `urlSession` integration (and all the swizzling done as part of this process).
///
/// However, even if the tracker doesn't have access to `URLSessionTaskTransactionMetrics` for
/// a given task, it should still log the request and response for it.
final class URLSessionTaskTracker {
    /// The tracker accesses task instances at various points during their lifetimes. These accesses may
    /// happen on one of the following queues:
    ///  * URLSession delegate queue
    ///  * URLSession work queue
    ///  * A queue used by a user to create/resume a given task
    ///
    /// For this reason, we synchronize access to tasks using a lock.
    private let lock = Lock()

    static let shared = URLSessionTaskTracker()

    func taskWillStart(_ task: URLSessionTask) {
        // Do not instrument requests performed by the Capture SDK itself.
        if task.cap_isCaptureAPI {
            return
        }

        // TODO(Augustyniak): Add supports for the these two types of tasks.
        if task is URLSessionStreamTask {
            return
        }

        if #available(iOS 13.0, *) {
            if task is URLSessionWebSocketTask {
                return
            }
        }

        self.lock.withLock {
            guard task.cap_requestInfo == nil else {
                // Defensive check in case we've logged a request for a given task already.
                return
            }

            guard let requestInfo = HTTPRequestInfo(task: task) else {
                return
            }

            task.cap_requestInfo = requestInfo
            URLSessionIntegration.shared.logger?.log(requestInfo, file: nil, line: nil, function: nil)
        }
    }

    // Observation: This method is called on `URLSession` delegate queue.
    func task(_ task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        self.lock.withLock {
            guard let requestInfo = task.cap_requestInfo else {
                return
            }

            // Avoid logging response for a given request more than once.
            task.cap_requestInfo = nil

            let httpResponse = HTTPResponse(httpURLResponse: task.response, error: task.error)
            let responseInfo = HTTPResponseInfo(
                requestInfo: requestInfo,
                response: httpResponse,
                metrics: HTTPRequestMetrics(metrics: metrics)
            )

            URLSessionIntegration.shared.logger?.log(responseInfo, file: nil, line: nil, function: nil)
        }
    }
}
