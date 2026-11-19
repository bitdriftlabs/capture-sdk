// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct PageViewMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let parentSpanId: String?
    let action: String
    let spanId: String
    let url: String
    let reason: String
    let durationMs: Double?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        var fields = makeFields(
            ("_url", url),
            ("_reason", reason)
        )
        fields.merge(makeURLFields(url: url)) { _, urlField in urlField }

        switch action {
        case "start":
            return .startSpan(
                id: spanId,
                name: "webview.pageView",
                level: .info,
                fields: fields,
                startTimeInterval: timestampTimeInterval,
                parentSpanID: nil
            )
        case "end":
            return .endSpan(
                id: spanId,
                result: .success,
                fields: fields,
                endTimeInterval: timestampTimeInterval
            )
        default:
            return nil
        }
    }
}
