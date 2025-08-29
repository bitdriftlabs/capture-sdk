// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides additional custom fields to add to HTTP request logs automatically sent
/// by using the URLSession integration.
public protocol URLSessionRequestFieldProvider {
    /// Provides extra fields for a given request.
    ///
    /// - Parameter request: The `URLRequest` being logged.
    /// - Returns: A dictionary of key-value pairs to add to the request log
    ///            that will be sent by the URLSession integration.
    func provideExtraFields(for request: URLRequest) -> [String: String]
}

/// Default implementation that provides no extra fields.
public struct DefaultURLSessionRequestFieldProvider: URLSessionRequestFieldProvider {
    public init() {}

    /// Always returns an empty dictionary, meaning no extra fields are added.
    ///
    /// - Parameter request: The `URLRequest` being logged.
    /// - Returns: An empty dictionary.
    public func provideExtraFields(for _: URLRequest) -> [String: String] {
        return [:]
    }
}
