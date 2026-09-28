// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The protocol used to intercept network requests that are about to be started.
///
/// `canInit(with:request:)`/`startLoading()`/`stopLoading()` are the mechanism used to inject
/// trace headers for all `URLSession` instances, including `URLSession.shared`. Mutating
/// `URLSessionTask.originalRequest` after task creation (a previous approach) only updates that
/// Swift-visible property -- it does not affect the bytes actually sent on the wire. Registering
/// a `URLProtocol` and rewriting the request in `startLoading()` is Apple's sanctioned mechanism
/// for this and is confirmed (via a minimal standalone repro) to actually affect the wire
/// request, including for the `URLSession.data(for:)` async convenience API, which bypasses
/// ordinary `URLSession` method swizzling entirely (also confirmed empirically: swizzling
/// `dataTask(with:completionHandler:)` never fired for `.data(for:)` callers, only for direct
/// callers of that exact method).
final class CaptureURLProtocol: URLProtocol, URLSessionDataDelegate {
    /// The authoritative check for `URLSession`-based loads (unlike `canInit(with: request:)`
    /// below, which Foundation appears to consult only for non-session-based/legacy loading --
    /// confirmed empirically: with this override hardcoded to `false`, `canInit(with: request:)`
    /// still fired and returned `true`, but `startLoading()` was never called).
    override class func canInit(with task: URLSessionTask) -> Bool {
        URLSessionTaskTracker.shared.taskWillStart(task)

        guard let request = task.originalRequest else {
            return false
        }

        return Self.shouldIntercept(request)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        Self.shouldIntercept(request)
    }

    private static func shouldIntercept(_ request: URLRequest) -> Bool {
        let headers = request.allHTTPHeaderFields
        guard !URLSessionTracePropagation.isURLProtocolRelayRequest(headers) else {
            // Our own relay request below, re-entering the URL Loading System -- never intercept
            // it again, or we'd recurse forever.
            return false
        }

        guard !URLSessionTracePropagation.isBitdriftInternalRequest(headers) else {
            // The SDK's own outbound traffic (Mux, OTel span export): `injectHeaders` already
            // no-ops for these, so intercepting just adds an unnecessary extra network hop.
            return false
        }

        let integration = URLSessionIntegration.shared
        return integration.tracePropagationMode != .disabled && integration.isTracingActive
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    private var relayTask: URLSessionDataTask?
    private lazy var relaySession = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)

    override func startLoading() {
        let integration = URLSessionIntegration.shared
        let result = URLSessionTracePropagation.injectHeaders(
            into: self.request,
            mode: integration.tracePropagationMode,
            isTracingActive: integration.isTracingActive,
            ignorePolicy: integration.requestIgnorePolicy
        )

        // The outer task is what the caller (and the existing `URLSessionTask.resume` swizzle,
        // which still fires normally for it) sees and logs -- stash the trace context there so
        // the existing request/response tracking and OTel span export pick it up unchanged.
        self.task?.cap_traceContext = result.traceContext

        guard let mutableRequest = (result.request as NSURLRequest).mutableCopy() as? NSMutableURLRequest
        else {
            self.client?.urlProtocol(self, didFailWithError: URLError(.unknown))
            return
        }
        mutableRequest.setValue("true", forHTTPHeaderField: URLSessionTracePropagation.urlProtocolRelayHeader)

        self.relayTask = self.relaySession.dataTask(with: mutableRequest as URLRequest)
        self.relayTask?.resume()
    }

    override func stopLoading() {
        self.relayTask?.cancel()
    }

    func urlSession(
        _: URLSession,
        dataTask _: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        completionHandler(.allow)
    }

    func urlSession(_: URLSession, dataTask _: URLSessionDataTask, didReceive data: Data) {
        self.client?.urlProtocol(self, didLoad: data)
    }

    func urlSession(_: URLSession, task _: URLSessionTask, didCompleteWithError error: Error?) {
        if let error {
            self.client?.urlProtocol(self, didFailWithError: error)
        } else {
            self.client?.urlProtocolDidFinishLoading(self)
        }
    }
}
