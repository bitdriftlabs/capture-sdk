// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides additional custom fields to add to http request logs automatically sent
/// by using the URLSession integration.
public protocol URLSessionRequestFieldProvider {
    /// @return a map of fields to add to the http request log that will be sent
    /// by the URLSession integration for this request.
    func provideExtraFields(for request: URLRequest) -> [String: String]
}

/// Default implementation that provides no extra fields
public struct DefaultURLSessionRequestFieldProvider: URLSessionRequestFieldProvider {
    public init() {}
    
    public func provideExtraFields(for request: URLRequest) -> [String: String] {
        return [:]
    }
}

