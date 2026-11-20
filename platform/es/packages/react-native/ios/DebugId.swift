// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import CryptoKit

enum DebugId {
    /// Generates a debug ID from the JavaScript bundle's MD5 hash.
    /// Returns nil if the bundle cannot be found or read.
    static func fromBundle() -> String? {
        guard let path = Bundle.main.path(forResource: "main", ofType: "jsbundle") else {
            return nil
        }
        guard let bundleData = try? Data(contentsOf: URL(fileURLWithPath: path)) else {
            return nil
        }
        let digest = Insecure.MD5.hash(data: bundleData)
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
