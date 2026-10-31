// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Reports errors to the network.
/// Implementations must be thread safe, as errors might be reported from any thread.
@objc
public protocol RemoteErrorReporting: AnyObject {
    /// Reports the error to the network.
    ///
    /// - parameter messageBufferPointer: The pointer to the message character array.
    /// - parameter fields:               The fields to attach to the reported error.
    func reportError(
        _ messageBufferPointer: UnsafePointer<UInt8>,
        fields: [String: String]
    )
}
