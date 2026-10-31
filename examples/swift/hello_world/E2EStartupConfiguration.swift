// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

enum E2EStartupConfiguration: Equatable {
    case normal
    case enabled(apiURL: URL, apiKey: String, run: String)
    case invalid

    static let errorMessage = "Invalid E2E configuration. SDK not started. " +
        "Set BITDRIFT_E2E=1, a loopback BITDRIFT_E2E_API_URL, " +
        "and nonempty BITDRIFT_E2E_API_KEY and BITDRIFT_E2E_RUN."

    static func resolve(environment: [String: String]) -> Self {
        #if targetEnvironment(simulator)
        let keys = ["BITDRIFT_E2E", "BITDRIFT_E2E_API_URL", "BITDRIFT_E2E_API_KEY", "BITDRIFT_E2E_RUN"]
        guard keys.contains(where: { environment[$0] != nil }) else {
            return .normal
        }

        guard environment["BITDRIFT_E2E"] == "1",
              let rawURL = environment["BITDRIFT_E2E_API_URL"],
              rawURL.rangeOfCharacter(from: .whitespacesAndNewlines) == nil,
              let components = URLComponents(string: rawURL),
              ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
              ["127.0.0.1", "localhost", "[::1]", "::1"].contains(components.host?.lowercased() ?? ""),
              components.user == nil, components.password == nil,
              components.query == nil, components.fragment == nil,
              components.path.isEmpty || components.path == "/",
              components.port.map({ (1...65535).contains($0) }) ?? true,
              let apiURL = components.url,
              let apiKey = environment["BITDRIFT_E2E_API_KEY"],
              !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let run = environment["BITDRIFT_E2E_RUN"],
              !run.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return .invalid
        }

        return .enabled(apiURL: apiURL, apiKey: apiKey, run: run)
        #else
        return .normal
        #endif
    }
}
