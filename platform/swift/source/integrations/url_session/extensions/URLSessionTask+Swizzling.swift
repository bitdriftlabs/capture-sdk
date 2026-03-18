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

        self.injectTraceHeadersIfNeeded()

        URLSessionTaskTracker.shared.taskWillStart(self)
        try? ObjCWrapper.doTry {
            self.delegate = ProxyURLSessionTaskDelegate(target: self.delegate)
        }
    }

    private func injectTraceHeadersIfNeeded() {
        let integration = URLSessionIntegration.shared
        let mode = integration.tracePropagationMode
        guard mode != .disabled, integration.isTracingActive else {
            return
        }

        let existingHeaders = self.originalRequest?.allHTTPHeaderFields
        if let existingTraceID = URLSessionTracePropagation.extractExistingTraceID(from: existingHeaders) {
            self.cap_traceContext = URLSessionTraceContext(traceID: existingTraceID, spanID: "")
            return
        }

        let traceContext = URLSessionTraceContext.make()
        self.cap_traceContext = traceContext

        guard let request = self.originalRequest,
              let mutableRequest = (request as NSURLRequest).mutableCopy() as? NSMutableURLRequest
        else {
            return
        }

        switch mode {
        case .w3c:
            mutableRequest.setValue(
                URLSessionTracePropagation.traceparentValue(traceContext: traceContext),
                forHTTPHeaderField: URLSessionTracePropagation.traceparentHeader
            )
        case .b3Single:
            mutableRequest.setValue(
                URLSessionTracePropagation.b3SingleValue(traceContext: traceContext),
                forHTTPHeaderField: URLSessionTracePropagation.b3Header
            )
        case .b3Multi:
            mutableRequest.setValue(traceContext.traceID, forHTTPHeaderField: URLSessionTracePropagation.xB3TraceIDHeader)
            mutableRequest.setValue(traceContext.spanID, forHTTPHeaderField: URLSessionTracePropagation.xB3SpanIDHeader)
            mutableRequest.setValue("1", forHTTPHeaderField: URLSessionTracePropagation.xB3SampledHeader)
        case .disabled:
            break
        }

        mutableRequest.setValue(traceContext.traceID, forHTTPHeaderField: URLSessionTracePropagation.traceIDHeader)
        try? ObjCWrapper.doTry {
            self.setValue(mutableRequest as URLRequest, forKey: "originalRequest")
        }
    }
}
