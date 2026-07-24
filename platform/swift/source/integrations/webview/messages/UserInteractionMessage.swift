// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct UserInteractionMessage: WebViewLoggableMessage, Equatable {
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

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        .log(
            level: interactionType == "rageClick" ? .warning : .debug,
            message: "webview.userInteraction",
            fields: makeFields(
                ("_interaction_type", interactionType),
                ("_tag_name", tagName),
                ("_is_clickable", String(isClickable)),
                ("_element_id", elementId),
                ("_class_name", className),
                ("_text_content", textContent),
                ("_click_count", clickCount.map(String.init)),
                ("_time_window_ms", timeWindowMs.map { String($0) })
            )
        )
    }
}
