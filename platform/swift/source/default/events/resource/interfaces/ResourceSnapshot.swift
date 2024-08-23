// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A protocol representing a snapshot of a tracked resource that can be serialized into a dictionary.
protocol ResourceSnapshot {
    /// Creates a dictionary representation of the receiver.
    ///
    /// - returns: The dictionary representing receiver.
    func toDictionary() -> [String: String]
}
