// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

typealias WebViewSerializableFields = [String: WebViewSerializableValue]

enum WebViewSerializableValue: Decodable, Equatable {
    case array([WebViewSerializableValue])
    case bool(Bool)
    case null
    case number(Double)
    case object(WebViewSerializableFields)
    case string(String)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if let object = try? container.decode(WebViewSerializableFields.self) {
            self = .object(object)
        } else if let array = try? container.decode([WebViewSerializableValue].self) {
            self = .array(array)
        } else if let bool = try? container.decode(Bool.self) {
            self = .bool(bool)
        } else if let integer = try? container.decode(Int.self) {
            self = .number(Double(integer))
        } else if let double = try? container.decode(Double.self) {
            self = .number(double)
        } else if let string = try? container.decode(String.self) {
            self = .string(string)
        } else if container.decodeNil() {
            self = .null
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported serializable value"
            )
        }
    }
}

enum WebViewMessageType: String, Decodable, Equatable {
    case customLog
    case bridgeReady
    case webVital
    case networkRequest
    case navigation
    case pageView
    case lifecycle
    case error
    case longTask
    case resourceError
    case console
    case promiseRejection
    case userInteraction
    case internalAutoInstrumentation
}

protocol WebViewMessage: Decodable {
    var tag: String { get }
    var v: Int { get }
    var type: WebViewMessageType { get }
    var timestamp: Int64 { get }
}

struct WebViewMessageParser: Decodable {
    let message: any WebViewMessage

    private enum CodingKeys: String, CodingKey {
        case type
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(WebViewMessageType.self, forKey: .type)
        self.message = try WebViewMessageCatalog.getType(type).init(from: decoder)
    }

    static func decode(from body: Any) throws -> any WebViewMessage {
        guard let body = body as? String else {
            throw ParsingError.invalidBody(typeDescription: String(describing: Swift.type(of: body)))
        }

        guard let data = body.data(using: .utf8) else {
            throw ParsingError.invalidJSONString
        }

        return try JSONDecoder().decode(Self.self, from: data).message
    }
}

extension WebViewMessageParser {
    enum ParsingError: Error, Equatable {
        case invalidBody(typeDescription: String)
        case invalidJSONString
    }
}

enum WebViewMessageCatalog {
    static func getType(_ type: WebViewMessageType) -> WebViewMessage.Type {
        switch type {
        case .customLog:
            return CustomLogMessage.self
        case .bridgeReady:
            return BridgeReadyMessage.self
        case .webVital:
            return WebVitalMessage.self
        case .networkRequest:
            return NetworkRequestMessage.self
        case .navigation:
            return NavigationMessage.self
        case .pageView:
            return PageViewMessage.self
        case .lifecycle:
            return LifecycleMessage.self
        case .error:
            return ErrorMessage.self
        case .longTask:
            return LongTaskMessage.self
        case .resourceError:
            return ResourceErrorMessage.self
        case .console:
            return ConsoleMessage.self
        case .promiseRejection:
            return PromiseRejectionMessage.self
        case .userInteraction:
            return UserInteractionMessage.self
        case .internalAutoInstrumentation:
            return InternalAutoInstrumentationMessage.self
        }
    }
}

struct WebViewInstrumentationConfig: Decodable, Equatable {
    let capturePageViews: Bool
    let captureNetworkRequests: Bool
    let captureWebVitals: Bool
    let captureNavigationEvents: Bool
    let captureConsoleLogs: Bool
    let captureLongTasks: Bool
    let captureUserInteractions: Bool
    let captureErrors: Bool
}

struct InternalAutoInstrumentationMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let event: String
}

struct CustomLogMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let level: String
    let message: String
    let fields: WebViewSerializableFields?
}

struct BridgeReadyMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let url: String
    let instrumentationConfig: WebViewInstrumentationConfig?
}

struct WebVitalMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let metric: WebVitalMetric
    let parentSpanId: String?
    let url: String?
}

struct WebVitalMetric: Decodable, Equatable {
    let name: String
    let value: Double
    let rating: String
    let delta: Double
    let id: String
    let navigationType: String
    let entries: [WebVitalEntry]
}

struct WebVitalEntry: Decodable, Equatable {
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

struct NetworkRequestMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let requestId: String
    let method: String
    let url: String
    let statusCode: Int
    let durationMs: Double
    let success: Bool
    let error: String?
    let requestType: String
    let timing: WebViewResourceTiming?
}

struct WebViewResourceTiming: Decodable, Equatable {
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
}

struct WebViewServerTiming: Decodable, Equatable {
    let name: String?
    let duration: Double?
    let description: String?
}

struct NavigationMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let fromUrl: String
    let toUrl: String
    let method: String
}

struct ErrorMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let name: String
    let message: String
    let stack: String?
    let filename: String?
    let lineno: Int?
    let colno: Int?
}

struct PageViewMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let action: String
    let spanId: String
    let url: String
    let reason: String
    let durationMs: Double?
}

struct LifecycleMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let event: String
    let performanceTime: Double
    let visibilityState: String?
}

struct LongTaskMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let durationMs: Double
    let startTime: Double
    let attribution: LongTaskAttribution?
}

struct LongTaskAttribution: Decodable, Equatable {
    let name: String?
    let containerType: String?
    let containerSrc: String?
    let containerId: String?
    let containerName: String?
}

struct ResourceErrorMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let resourceType: String
    let url: String
    let tagName: String
}

struct ConsoleMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let level: String
    let message: String
    let args: [String]?
}

struct PromiseRejectionMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let reason: String
    let stack: String?
}

struct UserInteractionMessage: WebViewMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let interactionType: String
    let tagName: String
    let elementId: String?
    let className: String?
    let textContent: String?
    let isClickable: Bool
    let clickCount: Int?
    let timeWindowMs: Double?
    let duration: Double?
}
