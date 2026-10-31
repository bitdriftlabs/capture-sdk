// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Provides additional custom fields to add to HTTP request logs automatically sent
/// by using the URLSession integration.
public protocol URLSessionRequestFieldProvider {
    /// Provides extra fields for a given request.
    ///
    /// - parameter request: The `URLRequest` being logged.
    ///
    /// - returns: A dictionary of key-value pairs to add to the request log
    ///            that will be sent by the URLSession integration.
    func provideExtraFields(for request: URLRequest) -> [String: String]
}
