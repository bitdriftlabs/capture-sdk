// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension Dictionary {
    /// Merges the receiver with a given dictionary, overwriting conflicting key-value pairs in the receiver
    /// with those from the provided dictionary and returning a new dictionary.
    ///
    /// - parameter other: The dictionary to merge with the receiver. In case of conflicting key-value pairs,
    ///                    the resulting dictionary prioritizes key-value pairs from this dictionary.
    ///
    /// - returns: A new dictionary created by merging key-value pairs from the receiver and the provided
    ///            dictionary.
    func mergedOverwritingConflictingKeys(_ other: Dictionary?) -> Dictionary {
        guard let other else {
            return self
        }
        return self.merging(other) { _, new in new }
    }

    /// Merges the receiver with a given dictionary, resolving key conflicts by omitting conflicting key-value
    /// pairs from the passed dictionary and returning a new dictionary.
    ///
    /// - parameter other: The dictionary to merge with the receiver. In case of conflicting key-value pairs,
    ///                    the resulting dictionary prioritizes key-value pairs from the receiver.
    ///
    /// - returns: A new dictionary created by merging key-value pairs from the receiver and the provided
    ///            dictionary.
    func mergedOmittingConflictingKeys(_ other: Dictionary?) -> Dictionary {
        guard let other else {
            return self
        }

        return self.merging(other) { old, _ in old }
    }

    /// Merges the two dictionaries together in place by adding key-values from the provided dictionary into
    /// the receiver. In case of key conflicts, values from the provided dictionary take precedence over
    /// their counterparts from the receiver.
    ///
    /// - parameter other: The dictionary to add the key-value to the receiver from.
    mutating func mergeOverwritingConflictingKeys(_ other: Dictionary) {
        self.merge(other) { _, new in new }
    }
}
