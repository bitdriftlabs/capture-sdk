// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import Security

enum URLSessionTracePropagationMode {
    case w3c
    case b3Single
    case b3Multi
    case datadog
    case disabled

    init(runtimeValue: String) {
        switch runtimeValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        {
        case "none":
            self = .disabled
        case "b3-single":
            self = .b3Single
        case "b3-multi":
            self = .b3Multi
        case "dd":
            self = .datadog
        case "w3c":
            self = .w3c
        default:
            self = .disabled
        }
    }
}

enum URLSessionTracePropagation {
    static let traceIDField = "_trace_id"
    static let traceparentHeader = "traceparent"
    static let bitdriftInitiatedTraceHeader = "x-bitdrift-initiated-trace"
    static let b3Header = "b3"
    static let xB3TraceIDHeader = "X-B3-TraceId"
    static let xB3SpanIDHeader = "X-B3-SpanId"
    static let xB3SampledHeader = "X-B3-Sampled"
    static let xDatadogTraceIDHeader = "x-datadog-trace-id"
    static let xDatadogSamplingPriorityHeader = "x-datadog-sampling-priority"

    static func extractSampledTraceID(
        from headers: [String: String]?,
        configuredPropagationMode: URLSessionTracePropagationMode
    ) -> String?
    {
        guard configuredPropagationMode == .datadog else {
            return extractSampledTraceID(from: headers)
        }

        return extractSampledDatadogTraceID(from: headers)
            ?? extractSampledW3CTraceIDAsDatadogDecimal(from: headers)
            ?? extractSampledTraceID(from: headers)
    }

    static func traceparentValue(traceContext: URLSessionTraceContext) -> String {
        "00-\(traceContext.traceID)-\(traceContext.spanID)-01"
    }

    static func b3SingleValue(traceContext: URLSessionTraceContext) -> String {
        "\(traceContext.traceID)-\(traceContext.spanID)-1"
    }

    /// Builds a copy of `request` with trace headers added, if applicable. Operates on a plain
    /// `URLRequest` (not a `URLSessionTask`) so it can run *before* a task exists -- mutating
    /// `URLSessionTask.originalRequest` after task creation (the previous approach) only updates
    /// that Swift-visible property; it does not affect the bytes actually sent on the wire.
    static func injectHeaders(
        into request: URLRequest,
        mode: URLSessionTracePropagationMode,
        isTracingActive: Bool,
        ignorePolicy: URLSessionRequestIgnorePolicy
    ) -> (request: URLRequest, traceContext: URLSessionTraceContext?) {
        guard mode != .disabled, isTracingActive else {
            return (request, nil)
        }

        let existingHeaders = request.allHTTPHeaderFields

        guard !URLSessionTracePropagation.isBitdriftInternalRequest(existingHeaders) else {
            return (request, nil)
        }

        guard !ignorePolicy.shouldIgnore(request) else {
            return (request, nil)
        }

        if URLSessionTracePropagation.hasExistingTraceHeaders(in: existingHeaders) {
            let traceContext = URLSessionTracePropagation
                .extractSampledTraceID(from: existingHeaders, configuredPropagationMode: mode)
                .map { URLSessionTraceContext(traceID: $0, spanID: "") }
            return (request, traceContext)
        }

        let traceContext = URLSessionTraceContext.make()

        guard let mutableRequest = (request as NSURLRequest).mutableCopy() as? NSMutableURLRequest else {
            return (request, traceContext)
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
            mutableRequest.setValue(
                traceContext.traceID,
                forHTTPHeaderField: URLSessionTracePropagation.xB3TraceIDHeader
            )
            mutableRequest.setValue(
                traceContext.spanID,
                forHTTPHeaderField: URLSessionTracePropagation.xB3SpanIDHeader
            )
            mutableRequest.setValue("1", forHTTPHeaderField: URLSessionTracePropagation.xB3SampledHeader)
        case .datadog:
            mutableRequest.setValue(
                traceContext.datadogTraceID,
                forHTTPHeaderField: URLSessionTracePropagation.xDatadogTraceIDHeader
            )
            mutableRequest.setValue(
                "2",
                forHTTPHeaderField: URLSessionTracePropagation.xDatadogSamplingPriorityHeader
            )
        case .disabled:
            break
        }

        mutableRequest.setValue("true", forHTTPHeaderField: URLSessionTracePropagation.bitdriftInitiatedTraceHeader)

        return (mutableRequest as URLRequest, traceContext)
    }

    private static let bitdriftAPIKeyHeader = "x-bitdrift-api-key"

    /// Marks the SDK's own outbound OTel span-export POSTs (see `OtelSpanExporter`) so they're
    /// excluded from trace-header injection and request/response tracking, the same way requests
    /// carrying `bitdriftAPIKeyHeader` are. A dedicated marker is used here, rather than reusing
    /// `bitdriftAPIKeyHeader`, because span export targets an arbitrary user-configured
    /// third-party endpoint -- sending bitdrift's own API key there would be both semantically
    /// wrong and a real credential leak.
    static let bitdriftInternalTelemetryHeader = "x-bitdrift-internal-telemetry"

    static func isBitdriftInternalRequest(_ headers: [String: String]?) -> Bool {
        headers?[bitdriftAPIKeyHeader] != nil || headers?[bitdriftInternalTelemetryHeader] != nil
    }

    /// Marks `CaptureURLProtocol`'s own internal relay request (the copy it fetches, with trace
    /// headers injected, to stand in for the real network call -- see `URLProtocol.swift`). The
    /// *outer* task the caller sees already gets logged normally via the existing
    /// `URLSessionTask.resume` swizzle; without this marker, the relay's own `resume()` call
    /// would swizzle-trigger a second, duplicate log entry for the same logical request.
    static let urlProtocolRelayHeader = "x-bitdrift-urlprotocol-relay"

    static func isURLProtocolRelayRequest(_ headers: [String: String]?) -> Bool {
        headers?[urlProtocolRelayHeader] != nil
    }

    static func hasExistingTraceHeaders(in headers: [String: String]?) -> Bool {
        guard let headers else { return false }
        return headers[traceparentHeader] != nil
            || headers[b3Header] != nil
            || headers[xB3TraceIDHeader] != nil
            || headers[xDatadogTraceIDHeader] != nil
    }

    /// Extracts the trace ID from known tracing headers only when the trace is sampled.
    ///
    /// - parameter headers: The request headers to inspect.
    ///
    /// - returns: The trace ID if the trace is sampled, or `nil` otherwise.
    static func extractSampledTraceID(from headers: [String: String]?) -> String? {
        guard let headers else { return nil }

        // W3C traceparent: 00-<traceId>-<spanId>-<flags>
        if let traceparent = headers[traceparentHeader] {
            let parts = traceparent.split(separator: "-")
            guard parts.count >= 4,
                  let flags = UInt8(parts[3], radix: 16),
                  flags & 0x01 == 1
            else {
                return nil
            }
            return String(parts[1])
        }

        // B3 single: <traceId>-<spanId>-<sampled>[-<parentSpanId>]
        if let b3 = headers[b3Header] {
            let parts = b3.split(separator: "-")
            guard parts.count >= 3 else { return nil }
            let sampled = parts[2]
            guard sampled == "1" || sampled == "d" else { return nil }
            return String(parts[0])
        }

        // B3 multi
        if let traceID = headers[xB3TraceIDHeader] {
            guard headers[xB3SampledHeader] == "1" else { return nil }
            return traceID
        }

        return extractSampledDatadogTraceID(from: headers)
    }

    private static func extractSampledW3CTraceIDAsDatadogDecimal(from headers: [String: String]?) -> String? {
        guard let headers else { return nil }
        guard let traceparent = headers[traceparentHeader] else { return nil }

        let parts = traceparent.split(separator: "-")
        guard parts.count >= 4,
              let flags = UInt8(parts[3], radix: 16),
              flags & 0x01 == 1
        else {
            return nil
        }

        let low64TraceID = String(parts[1].suffix(16))
        return UInt64(low64TraceID, radix: 16).map(String.init)
    }

    private static func extractSampledDatadogTraceID(from headers: [String: String]?) -> String? {
        guard let headers else { return nil }
        if let traceID = headers[xDatadogTraceIDHeader] {
            let sampled = headers[xDatadogSamplingPriorityHeader]
            // Datadog sampling priority values: 1 = AUTO_KEEP, 2 = USER_KEEP
            if sampled == "1" || sampled == "2" {
                return UInt64(traceID).flatMap { $0 > 0 ? traceID : nil }
            }
        }

        return nil
    }
}

protocol URLSessionRequestIgnorePolicy {
    func shouldIgnore(_ request: URLRequest?) -> Bool
}

struct RuntimeURLSessionIgnorePolicy: URLSessionRequestIgnorePolicy {
    private let ignorePaths: Set<String>
    private let requiredHeaders: Set<String>

    init(ignorePathsCSV: String, requiredHeadersCSV: String) {
        self.ignorePaths = Self.readCSV(ignorePathsCSV)
        self.requiredHeaders = Self.readCSV(requiredHeadersCSV)
    }

    func shouldIgnore(_ request: URLRequest?) -> Bool {
        guard let request else { return false }

        let hasIgnoredPaths = !ignorePaths.isEmpty
        let hasRequiredHeaders = !requiredHeaders.isEmpty
        if !hasIgnoredPaths && !hasRequiredHeaders {
            return false
        }

        let matchesIgnoredPath = request.url.map { ignorePaths.contains($0.path) } ?? false
        let matchesRequiredHeader = requiredHeaders.contains { request.value(forHTTPHeaderField: $0) != nil }

        if hasIgnoredPaths && hasRequiredHeaders {
            return matchesIgnoredPath && matchesRequiredHeader
        } else if hasIgnoredPaths {
            return matchesIgnoredPath
        }

        return matchesRequiredHeader
    }

    private static func readCSV(_ value: String) -> Set<String> {
        Set(value.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })
    }
}

struct URLSessionTraceContext {
    let traceID: String
    let spanID: String

    var datadogTraceID: String? {
        Self.hexToDecimal(String(self.traceID.suffix(16)))
    }

    static func make() -> URLSessionTraceContext {
        URLSessionTraceContext(traceID: Self.hexString(byteCount: 16), spanID: Self.hexString(byteCount: 8))
    }

    private static func hexString(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        if status != errSecSuccess {
            for index in bytes.indices {
                bytes[index] = UInt8.random(in: UInt8.min ... UInt8.max)
            }
        }

        let hexDigits = Array("0123456789abcdef".utf8)
        var output = [UInt8]()
        output.reserveCapacity(byteCount * 2)
        for byte in bytes {
            output.append(hexDigits[Int(byte >> 4)])
            output.append(hexDigits[Int(byte & 0x0f)])
        }

        return String(decoding: output, as: UTF8.self)
    }

    private static func hexToDecimal(_ hex: String) -> String? {
        UInt64(hex, radix: 16).map(String.init)
    }
}
