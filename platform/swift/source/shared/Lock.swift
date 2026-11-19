// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import os

/// An object that coordinates the operation of multiple threads of execution within
/// the same application.
final class Lock {
    // locks are faster than using a DispatchQueue
    private let underlyingLock: UnfairLocking

    init() {
        if #available(iOS 16.0, *) {
            self.underlyingLock = UnfairLock()
        } else {
            self.underlyingLock = LegacyUnfairLock()
        }
    }

    /// Acquires lock, performs a given closure and relinquishes the previously acquired
    /// lock.
    ///
    /// - parameter closure: A block to perform with acquired lock.
    ///
    /// - returns: The return value returned by the passed closure, if any.
    func withLock<T>(_ closure: () -> T) -> T {
        self.underlyingLock.withLock(closure)
    }
}

private protocol UnfairLocking {
    func withLock<T>(_ closure: () -> T) -> T
}

/// The implementation of an unfair lock that utilizes modern APIs. It should be used when available.
@available(iOS 16.0, *)
private final class UnfairLock: UnfairLocking {
    private let underlyingLock = OSAllocatedUnfairLock()

    func withLock<T>(_ closure: () -> T) -> T {
        self.underlyingLock.lock()
        defer { self.underlyingLock.unlock() }
        return closure()
    }
}

/// The unfair lock that utilizes legacy APIs. It should be avoided when possible.
private final class LegacyUnfairLock: UnfairLocking {
    private let underlyingLock: UnsafeMutablePointer<os_unfair_lock>

    init() {
        self.underlyingLock = UnsafeMutablePointer<os_unfair_lock>.allocate(capacity: 1)
        self.underlyingLock.initialize(to: os_unfair_lock())
    }

    deinit {
        self.underlyingLock.deinitialize(count: 1)
        self.underlyingLock.deallocate()
    }

    func withLock<T>(_ closure: () -> T) -> T {
        os_unfair_lock_lock(self.underlyingLock)
        defer { os_unfair_lock_unlock(self.underlyingLock) }
        return closure()
    }
}
