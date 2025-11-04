// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides additional custom fields to add to HTTP response logs automatically sent
/// by using the URLSession integration.
///
/// This protocol allows you to add fields based on the response, such as custom error messages
/// extracted from error response bodies, status codes, or response headers.
public protocol URLSessionResponseFieldProvider {
    /// Provides extra fields for a given response.
    ///
    /// - parameter response: The HTTP response received. The original request can be accessed via
    ///                       `response.url` or through the request associated with the response.
    ///
    /// - returns: A dictionary of key-value pairs to add to the response log
    ///            that will be sent by the URLSession integration.
    ///
    /// - note: The response body can be read from the response, but be aware that it may
    ///         have already been consumed by the application code. If you need to read the body,
    ///         consider using URLSession's delegate methods or dataTask completion handlers
    ///         to capture the body before it's consumed.
    func provideExtraFields(for response: HTTPURLResponse) -> [String: String]
}
