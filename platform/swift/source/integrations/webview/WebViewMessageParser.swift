// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

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

