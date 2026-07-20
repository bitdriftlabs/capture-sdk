// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct WebVitalMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let metric: WebVitalMetric
    let parentSpanId: String?
    let url: String?

    var ratingLogLevel: LogLevel {
        switch metric.rating {
        case "needs-improvement":
            return .info
        case "poor":
            return .warning
        default:
            return .debug
        }
    }

    var spanResult: SpanResult {
        switch metric.rating {
        case "good":
            return .success
        case "needs-improvement", "poor":
            return .failure
        default:
            return .unknown
        }
    }
}

extension WebVitalMessage: WebViewLoggableMessage {
    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        let parentSpanID = context.parentLoggerSpanID(for: parentSpanId)
        let fields = makeFields(
            includeTimestamp: false,
            ("_metric", metric.name),
            ("_value", String(metric.value)),
            ("_rating", metric.rating),
            ("_delta", String(metric.delta)),
            ("_metric_id", metric.id),
            ("_navigation_type", metric.navigationType),
            ("_span_parent_id", parentSpanID?.uuidString),
            ("_page_url", url),
            ("_entries", metric.entries.jsonString)
        )

        switch metric.name {
        case "LCP", "FCP", "TTFB", "INP":
            return .completeSpan(
                name: "webview.webVital",
                level: ratingLogLevel,
                fields: fields,
                startTimeInterval: timestampTimeInterval - (metric.value / 1_000),
                endTimeInterval: timestampTimeInterval,
                parentSpanID: parentSpanID,
                result: spanResult
            )
        default:
            return .log(level: ratingLogLevel, message: "webview.webVital", fields: fields)
        }
    }
}

private extension Array where Element == WebVitalEntry {
    var jsonString: String? {
        guard !isEmpty else {
            return nil
        }

        return try? encodeToString()
    }
}

struct WebVitalMetric: Codable, Equatable {
    let name: String
    let value: Double
    let rating: String
    let delta: Double
    let id: String
    let navigationType: String
    let entries: [WebVitalEntry]
}

struct WebVitalEntry: Codable, Equatable {
    let name: String?
    let entryType: String?
    let startTime: Double?
    let duration: Double?
    let initiatorType: String?
    let nextHopProtocol: String?
    let workerStart: Double?
    let redirectStart: Double?
    let redirectEnd: Double?
    let fetchStart: Double?
    let domainLookupStart: Double?
    let domainLookupEnd: Double?
    let connectStart: Double?
    let connectEnd: Double?
    let secureConnectionStart: Double?
    let requestStart: Double?
    let responseStart: Double?
    let responseEnd: Double?
    let transferSize: Int?
    let encodedBodySize: Int?
    let decodedBodySize: Int?
    let responseStatus: Int?
    let serverTiming: [WebViewServerTiming]?
    let renderTime: Double?
    let loadTime: Double?
    let size: Double?
    let id: String?
    let url: String?
    let value: Double?
    let hadRecentInput: Bool?
    let lastInputTime: Double?
    let interactionId: Int?
    let processingStart: Double?
    let processingEnd: Double?
}

struct WebViewServerTiming: Codable, Equatable {
    let name: String?
    let duration: Double?
    let description: String?
}
