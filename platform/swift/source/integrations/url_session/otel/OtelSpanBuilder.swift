// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The span-specific fields handed to `capture_build_otel_span_payload`, built once per completed
/// traced request. Mirrors Android's `OtelSpan`/`OtelSpanBuilder.kt`.
struct OtelSpan {
    let traceID: String
    let spanID: String
    let name: String
    let startTimeUnixNano: Int64
    let endTimeUnixNano: Int64
    let isError: Bool
    let attributes: OtelAttributes
}

enum OtelSpanBuilder {
    static func build(
        traceContext: URLSessionTraceContext,
        requestURL: URL?,
        requestInfo: HTTPRequestInfo,
        response: HTTPResponse,
        metrics: HTTPRequestMetrics,
        taskMetrics: URLSessionTaskMetrics,
        networkAttributes: NetworkAttributes,
        appStateAttributes: AppStateAttributes
    ) -> OtelSpan
    {
        let attributes = OtelAttributes()
        attributes.add("http.request.method", requestInfo.method)
        if let statusCode = response.statusCode {
            attributes.add("http.response.status_code", statusCode)
        }
        if let requestURL {
            attributes.add("url.full", requestURL.absoluteString)
        }
        if let template = requestInfo.path?.template {
            attributes.add("url.template", template)
        }
        if let host = requestInfo.host {
            attributes.add("server.address", host)
        }
        if let port = Self.port(for: requestURL) {
            attributes.add("server.port", port)
        }
        if let requestBodyBytesSentCount = metrics.requestBodyBytesSentCount {
            attributes.add("http.request.body.size", Int(requestBodyBytesSentCount))
        }
        if let responseBodyBytesReceivedCount = metrics.responseBodyBytesReceivedCount {
            attributes.add("http.response.body.size", Int(responseBodyBytesReceivedCount))
        }
        attributes.add("error.type", response.error.map { String(describing: type(of: $0)) } ?? "")
        let (protocolName, protocolVersion) = Self.splitProtocol(metrics.protocolName)
        if let protocolName {
            attributes.add("network.protocol.name", protocolName)
        }
        if let protocolVersion {
            attributes.add("network.protocol.version", protocolVersion)
        }
        attributes.add("network.connection.type", networkAttributes.otelConnectionType)
        attributes.add("ios.app.state", appStateAttributes.isForeground ? "foreground" : "background")

        let (startTimeUnixNano, endTimeUnixNano) = Self.timestamps(taskMetrics: taskMetrics)

        return OtelSpan(
            traceID: traceContext.traceID,
            spanID: traceContext.spanID,
            name: "\(requestInfo.method) \(requestInfo.path?.template ?? requestInfo.path?.value ?? "")",
            startTimeUnixNano: startTimeUnixNano,
            endTimeUnixNano: endTimeUnixNano,
            isError: response.error != nil,
            attributes: attributes
        )
    }

    /// `URLSessionTaskMetrics` carries real wall-clock `Date`s per transaction (unlike the
    /// monotonic timing captured elsewhere in this SDK), so those are used directly instead of
    /// stamping the current time at each call site.
    private static func timestamps(taskMetrics: URLSessionTaskMetrics) -> (start: Int64, end: Int64) {
        let nanosPerSecond = 1_000_000_000.0
        let now = Date()
        let start = taskMetrics.transactionMetrics.first?.fetchStartDate ?? now
        let end = taskMetrics.transactionMetrics.last?.responseEndDate ?? now
        return (
            Int64(start.timeIntervalSince1970 * nanosPerSecond),
            Int64(end.timeIntervalSince1970 * nanosPerSecond)
        )
    }

    /// `URLRequest`/`HTTPURLResponse`'s `url.port` is `nil` when the URL doesn't explicitly specify
    /// one, unlike OkHttp's `HttpUrl.port` on Android, which always resolves to the scheme's default.
    /// Mirror that behavior here.
    private static func port(for url: URL?) -> Int? {
        guard let url else {
            return nil
        }

        if let port = url.port {
            return port
        }

        switch url.scheme?.lowercased() {
        case "https":
            return 443
        case "http":
            return 80
        default:
            return nil
        }
    }

    /// Splits the combined protocol value (e.g. `"h2"`, `"http/1.1"`) into name/version. Mirrors
    /// Android's `OtelSpanBuilder.splitProtocol`.
    private static func splitProtocol(_ protocolName: String?) -> (name: String?, version: String?) {
        guard let value = protocolName else {
            return (nil, nil)
        }

        switch value {
        case "h2", "h2_prior_knowledge":
            return ("http", "2")
        case "h3":
            return ("http", "3")
        default:
            if value.hasPrefix("http/") {
                return ("http", String(value.dropFirst("http/".count)))
            }
            return (value, nil)
        }
    }
}
