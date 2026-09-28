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

    static func enrichExtraFields(
        _ extraFields: Fields?,
        with traceContext: URLSessionTraceContext?
    ) -> Fields?
    {
        guard let traceContext, !traceContext.traceID.isEmpty else {
            return extraFields
        }

        var updatedExtraFields = extraFields ?? [:]
        updatedExtraFields[URLSessionTracePropagation.traceIDField] = traceContext.traceID

        return updatedExtraFields
    }

    /// Returns whatever trace context has *already* been established for this task -- it does
    /// not generate one. `CaptureURLProtocol.startLoading()` is the sole source of truth for
    /// `cap_traceContext`, since it's the only place that actually controls what goes out on the
    /// wire. This is called from `taskWillStart`, which (via the `URLSessionTask.resume` swizzle)
    /// always runs *before* `CaptureURLProtocol` gets a chance to run for `URLSession.data(for:)`
    /// and similar calls -- generating a fallback trace context here used to race with it,
    /// producing two different trace IDs for the same request: a throwaway one on this request's
    /// own log line, and the real one -- used for the actual header and OTel span export --
    /// everywhere else.
    private static func ensureTraceContext(for task: URLSessionTask) -> URLSessionTraceContext? {
        task.cap_traceContext
    }

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

        let integration = URLSessionIntegration.shared
        let ignorePolicy = integration.requestIgnorePolicy
        if ignorePolicy.shouldIgnore(task.originalRequest) {
            return
        }

        self.lock.withLock {
            guard task.cap_requestInfo == nil else {
                // Defensive check in case we've logged a request for a given task already.
                return
            }

            var extraFields: Fields?
            if let originalRequest = task.originalRequest {
                extraFields = integration.requestFieldProvider?.provideExtraFields(for: originalRequest)
            }
            extraFields = Self.enrichExtraFields(extraFields, with: Self.ensureTraceContext(for: task))
            guard let requestInfo = HTTPRequestInfo(task: task, extraFields: extraFields) else {
                return
            }

            task.cap_requestInfo = requestInfo
            integration.logger?.log(requestInfo, file: nil, line: nil, function: nil)
        }
    }

    // Observation: This method is called on `URLSession` delegate queue.
    func task(_ task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        if !Self.supports(task: task) {
            return
        }

        // `exportOtelSpanIfNeeded` below resumes a *new* `URLSessionTask` (the exporter's own
        // POST), which re-enters this same class's `cap_resume` -> `taskWillStart` on the same
        // thread synchronously (before any actual async I/O begins). `self.lock` is a plain
        // `os_unfair_lock` (non-reentrant): calling it again while still held aborts the process.
        // So the export call must happen strictly after this method's own `withLock` has
        // returned, not from within its closure.
        let otelExportInfo: (
            traceContext: URLSessionTraceContext?, requestURL: URL?, requestInfo: HTTPRequestInfo,
            httpResponse: HTTPResponse, metrics: HTTPRequestMetrics
        )? = self.lock.withLock {
            guard let requestInfo = task.cap_requestInfo else {
                return nil
            }

            // Avoid logging response for a given request more than once.
            task.cap_requestInfo = nil

            let httpResponse = HTTPResponse(httpURLResponse: task.response, error: task.error)
            var extraFields: Fields?
            if let httpURLResponse = task.response as? HTTPURLResponse {
                extraFields = URLSessionIntegration.shared.responseFieldProvider?.provideExtraFields(
                    for: httpURLResponse
                )
            }
            extraFields = Self.enrichExtraFields(extraFields, with: task.cap_traceContext)
            let responseInfo = HTTPResponseInfo(
                requestInfo: requestInfo,
                response: httpResponse,
                metrics: HTTPRequestMetrics(metrics: metrics),
                extraFields: extraFields
            )

            URLSessionIntegration.shared.logger?.log(responseInfo, file: nil, line: nil, function: nil)

            let traceContext = task.cap_traceContext
            let requestURL = task.originalRequest?.url
            let requestMetrics = HTTPRequestMetrics(metrics: metrics)

            task.cap_traceContext = nil

            return (traceContext, requestURL, requestInfo, httpResponse, requestMetrics)
        }

        if let otelExportInfo {
            self.exportOtelSpanIfNeeded(
                traceContext: otelExportInfo.traceContext,
                requestURL: otelExportInfo.requestURL,
                requestInfo: otelExportInfo.requestInfo,
                httpResponse: otelExportInfo.httpResponse,
                metrics: otelExportInfo.metrics,
                taskMetrics: metrics
            )
        }
    }

    /// Builds and exports an OTel span for this request if OTel export is configured and this
    /// request carries a trace context bitdrift itself injected (`traceContext.spanID` is empty
    /// for a trace ID merely *observed* from an existing upstream header -- see
    /// `URLSessionTaskTracker.ensureTraceContext` -- so there is no span ID to export).
    private func exportOtelSpanIfNeeded(
        traceContext: URLSessionTraceContext?,
        requestURL: URL?,
        requestInfo: HTTPRequestInfo,
        httpResponse: HTTPResponse,
        metrics: HTTPRequestMetrics,
        taskMetrics: URLSessionTaskMetrics
    ) {
        let integration = URLSessionIntegration.shared
        guard let exporter = integration.otelSpanExporter,
              let traceContext, !traceContext.spanID.isEmpty,
              let networkAttributes = integration.networkAttributes,
              let appStateAttributes = integration.appStateAttributes
        else {
            return
        }

        let span = OtelSpanBuilder.build(
            traceContext: traceContext,
            requestURL: requestURL,
            requestInfo: requestInfo,
            response: httpResponse,
            metrics: metrics,
            taskMetrics: taskMetrics,
            networkAttributes: networkAttributes,
            appStateAttributes: appStateAttributes
        )
        exporter.export(span)
    }
}
