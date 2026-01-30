// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Base structure for all WebView bridge messages.
/// All messages have a version, type, and timestamp.
struct WebViewBridgeMessage: Codable {
    let v: Int
    let tag: String?
    let type: String?
    let timestamp: Int64?
    
    // bridgeReady
    let url: String?
    let instrumentationConfig: [String: AnyCodable]?
    
    // webVital
    let metric: WebVitalMetric?
    let parentSpanId: String?
    
    // networkRequest
    let method: String?
    let statusCode: Int?
    let durationMs: Int64?
    let success: Bool?
    let error: String?
    let requestType: String?
    let timing: NetworkTiming?
    
    // navigation
    let fromUrl: String?
    let toUrl: String?
    
    // pageView
    let action: String?
    let spanId: String?
    let reason: String?
    
    // lifecycle
    let event: String?
    let performanceTime: Double?
    let visibilityState: String?
    
    // error
    let name: String?
    let message: String?
    let stack: String?
    let filename: String?
    let lineno: Int?
    let colno: Int?
    
    // longTask
    let startTime: Double?
    let attribution: LongTaskAttribution?
    
    // resourceError
    let resourceType: String?
    let tagName: String?
    
    // console
    let level: String?
    let args: [String]?
    
    // promiseRejection
    let reason: String?
    
    // userInteraction
    let interactionType: String?
    let elementId: String?
    let className: String?
    let textContent: String?
    let isClickable: Bool?
    let clickCount: Int?
    let timeWindowMs: Int?
    
    // customLog
    let fields: [String: AnyCodable]?
}

/// Web Vital metric data from the web-vitals library.
struct WebVitalMetric: Codable {
    let name: String?
    let value: Double?
    let rating: String?
    let delta: Double?
    let id: String?
    let navigationType: String?
    let entries: [WebVitalEntry]?
}

/// Performance entry associated with a web vital metric.
/// Contains different fields depending on the metric type.
struct WebVitalEntry: Codable {
    // Common fields
    let startTime: Double?
    let entryType: String?
    
    // LCP-specific
    let element: String?
    let url: String?
    let size: Int64?
    let renderTime: Double?
    let loadTime: Double?
    
    // FCP-specific
    let name: String?
    
    // TTFB-specific (PerformanceNavigationTiming)
    let domainLookupStart: Double?
    let domainLookupEnd: Double?
    let connectStart: Double?
    let connectEnd: Double?
    let secureConnectionStart: Double?
    let requestStart: Double?
    let responseStart: Double?
    
    // INP-specific
    let processingStart: Double?
    let processingEnd: Double?
    let duration: Double?
    let interactionId: Int64?
    
    // CLS-specific
    let value: Double?
}

/// Network timing data from the Performance API.
struct NetworkTiming: Codable {
    let transferSize: Int64?
    let dnsMs: Int64?
    let tlsMs: Int64?
    let connectMs: Int64?
    let ttfbMs: Int64?
}

/// Long task attribution data.
struct LongTaskAttribution: Codable {
    let name: String?
    let containerType: String?
    let containerSrc: String?
    let containerId: String?
    let containerName: String?
}

/// A type-erased wrapper for arbitrary Codable values
struct AnyCodable: Codable {
    let value: Any
    
    init(_ value: Any) {
        self.value = value
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        
        if container.decodeNil() {
            value = NSNull()
        } else if let bool = try? container.decode(Bool.self) {
            value = bool
        } else if let int = try? container.decode(Int.self) {
            value = int
        } else if let double = try? container.decode(Double.self) {
            value = double
        } else if let string = try? container.decode(String.self) {
            value = string
        } else if let array = try? container.decode([AnyCodable].self) {
            value = array.map(\.value)
        } else if let dictionary = try? container.decode([String: AnyCodable].self) {
            value = dictionary.mapValues(\.value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unable to decode value"
            )
        }
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        
        switch value {
        case is NSNull:
            try container.encodeNil()
        case let bool as Bool:
            try container.encode(bool)
        case let int as Int:
            try container.encode(int)
        case let double as Double:
            try container.encode(double)
        case let string as String:
            try container.encode(string)
        case let array as [Any]:
            try container.encode(array.map { AnyCodable($0) })
        case let dictionary as [String: Any]:
            try container.encode(dictionary.mapValues { AnyCodable($0) })
        default:
            let context = EncodingError.Context(
                codingPath: container.codingPath,
                debugDescription: "Unable to encode value of type \(type(of: value))"
            )
            throw EncodingError.invalidValue(value, context)
        }
    }
}
