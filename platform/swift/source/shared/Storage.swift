// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private let kSuiteName = "io.bitdrift.storage.platform"

/// Storage abstraction to persist data in the device. This interface provides a strongly typed
/// option to access an underlying storage system. The only supported mechanism at the moment
/// is UserDefaults.
struct Storage: StorageProvider {
    // From the documentation of the `UserDefaults(suiteName:)` it seems that it should not fail as long
    // as we do not pass "current application's bundle identifier, NSGlobalDomain, or the corresponding
    // `CFPreferences`` constants" as its argument but to avoid having to deal with optional `UserDefaults`
    // we do fallback to `UserDefaults.standard`.
    static let shared = Storage(storage: UserDefaults(suiteName: kSuiteName) ?? .standard)

    private let storage: UserDefaults

    func get<T>(_ key: StorageKey<T>) -> T? {
        return self.storage.object(forKey: key.key) as? T
    }

    func set<T>(_ value: T?, forKey key: StorageKey<T>) {
        self.storage.set(value, forKey: key.key)
    }

    // Clears all of the key-value pairs. Intended to be used for testing purposes only.
    func clear() {
        // Used by the platform layer.
        self.storage.removePersistentDomain(forName: kSuiteName)
        // Used by the Rust layer.
        self.storage.removePersistentDomain(forName: "io.bitdrift.storage")
        self.storage.synchronize()
    }
}
