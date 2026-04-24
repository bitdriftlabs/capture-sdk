// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct WebViewScriptConfiguration: Codable {
    private static let encoder: JSONEncoder = JSONEncoder()

    private let capturePageViews: Bool
    private let captureNetworkRequests: Bool
    private let captureNavigationEvents: Bool
    private let captureWebVitals: Bool
    private let captureLongTasks: Bool
    private let captureConsoleLogs: Bool
    private let captureUserInteractions: Bool
    private let captureErrors: Bool

    init(
        capturePageViews: Bool = true,
        captureNetworkRequests: Bool = true,
        captureNavigationEvents: Bool = true,
        captureWebVitals: Bool = true,
        captureLongTasks: Bool = true,
        captureConsoleLogs: Bool = true,
        captureUserInteractions: Bool = true,
        captureErrors: Bool = true
    ) {
        self.capturePageViews = capturePageViews
        self.captureNetworkRequests = captureNetworkRequests
        self.captureNavigationEvents = captureNavigationEvents
        self.captureWebVitals = captureWebVitals
        self.captureLongTasks = captureLongTasks
        self.captureConsoleLogs = captureConsoleLogs
        self.captureUserInteractions = captureUserInteractions
        self.captureErrors = captureErrors
    }

    func toJSONString() -> String {
        let json = try? Self.encoder.encode(self)
        if let json {
            return String(data: json, encoding: .utf8) ?? "{}"
        }
        return "{}"
    }
}
