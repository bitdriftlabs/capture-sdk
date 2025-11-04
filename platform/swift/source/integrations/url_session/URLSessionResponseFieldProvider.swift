// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides additional custom fields to add to HTTP response logs automatically sent
/// by using the URLSession integration.
public protocol URLSessionResponseFieldProvider {
    /// Provides extra fields for a given response.
    ///
    /// - parameter request:  The original `URLRequest` that triggered this response.
    /// - parameter response: The HTTP response received, or `nil` if the request failed before receiving a response.
    ///
    /// - returns: A dictionary of key-value pairs to add to the response log
    ///            that will be sent by the URLSession integration.
    func provideExtraFields(for request: URLRequest, response: HTTPURLResponse?) -> [String: String]
}
