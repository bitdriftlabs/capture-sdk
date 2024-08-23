// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// This type represents a storage key with a type `<T>` expected value.
struct StorageKey<T> {
    let key: String
}

/// The interface for storing and retrieving primitives from disk.
protocol StorageProvider {
    /// Retrieves the value for a given key.
    ///
    /// - parameter key: The key to retrieve the value for.
    ///
    /// - returns: The retrieve value, if any.
    func get<T>(_ key: StorageKey<T>) -> T?

    /// Sets a specified value for a given key.
    ///
    /// - parameter value: The value to set.
    /// - parameter key:   The key to set the value for.
    func set<T>(_ value: T?, forKey key: StorageKey<T>)
}
