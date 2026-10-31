// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

protocol WebViewLoggableMessage: WebViewMessage {
    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction?
}

struct WebViewLoggingContext {
    let currentPageViewSpanID: String?
    var nativePageViewSpanIDs: [String: UUID] = [:]

    func parentSpanID(for webViewSpanID: String?) -> UUID? {
        guard let spanID = webViewSpanID ?? currentPageViewSpanID else {
            return nil
        }

        return nativePageViewSpanIDs[spanID] ?? UUID(uuidString: spanID)
    }
}

enum WebViewLoggingAction {
    case log(level: LogLevel, message: String, fields: Fields, type: Capture.Logger.LogType = .normal)
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
