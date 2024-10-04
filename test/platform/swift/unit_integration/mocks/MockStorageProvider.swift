// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation

public final class MockStorageProvider: StorageProvider {
    private var storage = [String: Any]()

    public init() {}

    public func get<T>(_ key: StorageKey<T>) -> T? {
        return self.storage[key.key] as? T
    }

    public func set<T>(_ value: T?, forKey key: StorageKey<T>) {
        self.storage[key.key] = value
    }
}
