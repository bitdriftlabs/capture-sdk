// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

private let kHeadersFieldKeyPrefix = "_headers"
private let kDisallowedHeaderKeys: Set<String> = ["authorization", "proxy-authorization"]

struct HTTPHeaders {
    private init() {}

    static func normalizeHeaders(_ headers: [String: String]) -> [String: String] {
        return Dictionary(uniqueKeysWithValues: self.normalizeHeaders(headers))
    }

    static func normalizeHeaders(_ headers: [String: String]) -> [(String, String)] {
        return headers.compactMap { key, value in
            if kDisallowedHeaderKeys.contains(key.lowercased()) {
                return nil
            }

            return ("\(kHeadersFieldKeyPrefix).\(key)", value)
        }
    }
}
