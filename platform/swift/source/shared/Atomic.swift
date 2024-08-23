// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Dispatch

/// Provides thread-safe access to a value.
final class Atomic<T> {
    private let lock = Lock()
    private var valueStorage: T

    /// Atomic variable initialized with passed value.
    /// Note: Doing lookups on wrapped collections via `someAtomic.value["someKey"]` is not thread safe.
    ///
    /// - parameter value: The initial value.
    init(_ value: T) {
        self.valueStorage = value
    }

    /// Thread-safe in-place mutation of the underlying value.
    ///
    /// - parameter closure: The closure executed inside of the critical section.
    ///                      It's called with a stored value as an inout argument.
    ///                      Mutating or assigning new value will change stored value.
    ///                      NOTE: `value` cannot be accessed from within this closure,
    ///                      since that will cause a deadlock.
    ///
    /// - returns: The updated value.
    @discardableResult
    func update(_ closure: (inout T) -> Void) -> T {
        self.withLock { value in
            closure(&value)
            return value
        }
    }

    /// Thread-safe synchronous accessor for the value.
    ///
    /// - returns: The underlying value.
    func load() -> T {
        return self.withLock { $0 }
    }

    private func withLock<U>(_ closure: (inout T) -> U) -> U {
        self.lock.withLock {
            closure(&self.valueStorage)
        }
    }
}
