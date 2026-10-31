// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

struct LongTaskMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let parentSpanId: String?
    let durationMs: Double
    let startTime: Double
    let attribution: LongTaskAttribution?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        let logLevel: LogLevel = switch durationMs {
        case 200...: .warning
        case 100...: .info
        default: .info
        }

        return .log(
            level: logLevel,
            message: "webview.longTask",
            fields: makeFields(
                context: context,
                ("_duration_ms", String(durationMs)),
                ("_start_time", String(startTime)),
                ("_attribution_name", attribution?.name),
                ("_container_type", attribution?.containerType),
                ("_container_src", attribution?.containerSrc),
                ("_container_id", attribution?.containerId),
                ("_container_name", attribution?.containerName)
            ),
            type: .ux
        )
    }
}

struct LongTaskAttribution: Codable, Equatable {
    let name: String?
    let containerType: String?
    let containerSrc: String?
    let containerId: String?
    let containerName: String?
}
