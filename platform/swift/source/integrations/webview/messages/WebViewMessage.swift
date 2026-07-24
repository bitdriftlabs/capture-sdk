// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

protocol WebViewMessage: Decodable {
    var tag: String { get }
    var v: Int { get }
    var type: WebViewMessageType { get }
    var timestamp: Int64 { get }
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

private enum WebViewLogging {
    static let source = "webview"
}

extension WebViewMessage {
    func makeBaseFields(includeTimestamp: Bool = true) -> Fields {
        var fields: Fields = ["_source": WebViewLogging.source]
        if includeTimestamp {
            fields["_timestamp"] = String(timestamp)
        }
        return fields
    }

    func makeFields(
        includeTimestamp: Bool = true,
        _ values: (String, String?)...
    ) -> Fields {
        var fields = makeBaseFields(includeTimestamp: includeTimestamp)

        for (key, value) in values {
            guard let value else {
                continue
            }
            fields[key] = value
        }

        return fields
    }

    var timestampTimeInterval: TimeInterval {
        TimeInterval(timestamp) / 1_000
    }
}
