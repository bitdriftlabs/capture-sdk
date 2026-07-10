// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct NavigationMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let fromUrl: String
    let toUrl: String
    let method: String

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        .log(
            level: .debug,
            message: "webview.navigation",
            fields: makeFields(
                ("_fromUrl", fromUrl),
                ("_toUrl", toUrl),
                ("_method", method)
            )
        )
    }
}
