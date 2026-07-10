// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct ConsoleMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let level: String
    let message: String
    let args: [String]?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        let logLevel: LogLevel = switch level {
        case "error": .error
        case "warn": .warning
        case "info": .info
        default: .debug
        }

        return .log(
            level: logLevel,
            message: "webview.console",
            fields: makeFields(
                ("_level", level),
                ("_message", message),
                ("_args", args?.prefix(5).joined(separator: ", "))
            )
        )
    }
}
