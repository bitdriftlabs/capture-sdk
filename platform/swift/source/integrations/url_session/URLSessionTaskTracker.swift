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

    /// Ensures the given task type is supported by our current network instrumentation. Some of these don't
    /// have all properties we
    /// access, and those are only known at runtime. To play safe, we only check the positive case here we
    /// know we support.
    ///
    /// TODO(fz): Add supports for other types of tasks (stream, download, avdownload, etc).
    ///
    /// - parameter task: The instance of the task to check for support.
    ///
    /// - returns: `true` if the task is supported, `false` otherwise.
    static func supports(task: URLSessionTask) -> Bool {
        return (
            task is URLSessionDataTask ||
                task is URLSessionDownloadTask ||
                task is URLSessionUploadTask
        )
    }

    func taskWillStart(_ task: URLSessionTask) {
        if !Self.supports(task: task) {
            return
        }

        self.lock.withLock {
            guard task.cap_requestInfo == nil else {
                // Defensive check in case we've logged a request for a given task already.
                return
            }

            var extraFields: Fields?
            if let originalRequest = task.originalRequest {
                extraFields = URLSessionIntegration.shared.requestFieldProvider?.provideExtraFields(for: originalRequest)
            }
            guard let requestInfo = HTTPRequestInfo(task: task, extraFields: extraFields) else {
                return
            }

            task.cap_requestInfo = requestInfo
            URLSessionIntegration.shared.logger?.log(requestInfo, file: nil, line: nil, function: nil)
        }
    }

    // Observation: This method is called on `URLSession` delegate queue.
    func task(_ task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        if !Self.supports(task: task) {
            return
        }

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
