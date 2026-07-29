// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

protocol WebViewLoggableMessage: WebViewMessage {
    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction?
}

struct WebViewLoggingContext {
    let currentPageViewSpanID: String?
    let activePageViewSpans: [String: Span]

    func parentLoggerSpanID(for webViewSpanID: String?) -> UUID? {
        if let webViewSpanID, let activeSpan = activePageViewSpans[webViewSpanID] {
            return activeSpan.id
        }

        if let webViewSpanID {
            return UUID(uuidString: webViewSpanID)
        }

        guard let currentPageViewSpanID else {
            return nil
        }

        return activePageViewSpans[currentPageViewSpanID]?.id
    }
}

enum WebViewLoggingAction {
    case log(level: LogLevel, message: String, fields: Fields)
    case network(request: HTTPRequestInfo, response: HTTPResponseInfo)
    case startSpan(
            id: String,
            name: String,
            level: LogLevel,
            fields: Fields,
            startTimeInterval: TimeInterval?,
            parentSpanID: UUID?
         )
    case endSpan(
            id: String,
            result: SpanResult,
            fields: Fields,
            endTimeInterval: TimeInterval?
         )
    case completeSpan(
            name: String,
            level: LogLevel,
            fields: Fields,
            startTimeInterval: TimeInterval?,
            endTimeInterval: TimeInterval?,
            parentSpanID: UUID?,
            result: SpanResult
         )
}
