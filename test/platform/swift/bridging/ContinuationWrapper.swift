// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Error type providing a description of why the continuation failed.
/// Used in conjunction with `ContinuationHelper`.
public struct ContinuationError: Error {
    let rawDescription: String

    init(description: String) {
        self.rawDescription = description
    }
}

extension ContinuationError: LocalizedError {
    public var errorDescription: String? {
        return "ContinuationError: \(self.rawDescription)"
    }
}

/// Wrapper around a CheckedContinuation to allow Rust to interact
/// with an @objc class that simplifies resuming/failing a continuation.
@objc
public class ContinuationWrapper: NSObject {
    let continuation: CheckedContinuation<Int32, any Error>

    public init(continuation: CheckedContinuation<Int32, any Error>) {
        self.continuation = continuation
    }

    @objc
    public func resume(value: Int32) {
        self.continuation.resume(returning: value)
    }

    @objc
    public func fail(value: String) {
        self.continuation.resume(throwing: ContinuationError(description: value))
    }
}
