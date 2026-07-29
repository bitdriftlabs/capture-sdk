// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct PromiseRejectionMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let reason: String
    let stack: String?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        .log(
            level: .error,
            message: "webview.promiseRejection",
            fields: makeFields(
                ("_reason", reason),
                ("_stack", stack)
            )
        )
    }
}
