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
    case disabled

    init(runtimeValue: String) {
        switch runtimeValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        {
        case "b3-single":
            self = .b3Single
        case "b3-multi":
            self = .b3Multi
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
    static let b3Header = "b3"
    static let xB3TraceIDHeader = "X-B3-TraceId"
    static let xB3SpanIDHeader = "X-B3-SpanId"
    static let xB3SampledHeader = "X-B3-Sampled"

    static func traceparentValue(traceContext: URLSessionTraceContext) -> String {
        "00-\(traceContext.traceID)-\(traceContext.spanID)-01"
    }

    static func b3SingleValue(traceContext: URLSessionTraceContext) -> String {
        "\(traceContext.traceID)-\(traceContext.spanID)-1"
    }

    static func hasExistingTraceHeaders(in headers: [String: String]?) -> Bool {
        guard let headers else { return false }
        return headers[traceparentHeader] != nil
            || headers[b3Header] != nil
            || headers[xB3TraceIDHeader] != nil
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

        return nil
    }
}

struct URLSessionTraceContext {
    let traceID: String
    let spanID: String

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
}
