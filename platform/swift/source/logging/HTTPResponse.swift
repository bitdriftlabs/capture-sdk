// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The result of performing an HTTP Request.
public final class HTTPResponse: NSObject {
    /// The result of an HTTP Request.
    public enum HTTPResult: Int {
        case success
        case failure
        case canceled
    }

    let host: String?
    let path: HTTPURLPath?
    let query: String?
    let headers: [String: String]?

    /// The result of a given HTTP request.
    let result: HTTPResult
    let statusCode: Int?
    let error: Error?

    /// Initialize a new instance of the receiver.
    ///
    /// It's recommended that you use `init(httpURLResponse:error:)` convenience initializer if you work with
    /// `URLSession` response types such as `HTTPURLResponse`.
    ///
    /// - parameter result:     The result of the operation.
    /// - parameter host:       The host (e.g. "example.com").
    /// - parameter path:       The path (e.g. "/v1/test/ping").
    /// - parameter query:      The query (e.g. "utm_source=test").
    /// - parameter headers:    The response headers.
    /// - parameter statusCode: The server side status code that indicate whether an HTTP request has been
    ///                         successfully completed, if any.
    /// - parameter error:      The error received as the result of performing a network request, if any.
    ///                         The framework makes an attempt to cast a given error to `NSError` to
    ///                         obtain the value of it's `code` property. `NSError` is an underlying
    ///                         type of an error used by `URLSession`.
    public init(
        result: HTTPResult,
        host: String? = nil,
        path: HTTPURLPath? = nil,
        query: String? = nil,
        headers: [AnyHashable: Any]? = nil,
        statusCode: Int?,
        error: Error?
    ) {
        self.result = result
        self.host = host
        self.path = path
        self.query = query

        if let headers {
            self.headers = Dictionary(uniqueKeysWithValues: headers.compactMap { key, value in
                guard let key = key as? String, let value = value as? String else {
                    return nil
                }

                return (key, value)
            })
        } else {
            self.headers = nil
        }

        self.statusCode = statusCode
        self.error = error
    }
}

extension HTTPResponse {
    /// Initializes a new instance of the receiver. Provides an easy-to-use way
    /// to initialize `HTTPResult` when using `URLSession` to perform HTTP requests.
    ///
    /// - parameter httpURLResponse: The received response, if any.
    /// - parameter error:           The error received as the result of performing a network request, if any.
    ///                              The framework makes an attempt to cast a given error to `NSError` to
    ///                              obtain the value of it's `code` property. `NSError` is an underlying
    ///                              type of an error used by `URLSession`.
    public convenience init(httpURLResponse: URLResponse?, error: Error?) {
        let status = HTTPStatus(response: httpURLResponse, error: error)
        let response = (httpURLResponse as? HTTPURLResponse)

        self.init(
            result: status.result,
            host: response?.url?.host,
            path: response?.url?.normalizedPath().flatMap { HTTPURLPath(value: $0, template: nil) },
            query: response?.url?.query,
            headers: response?.allHeaderFields,
            statusCode: status.serverStatusCode,
            error: error
        )
    }
}

private extension HTTPStatus {
    var result: HTTPResponse.HTTPResult {
        if self.isSuccess {
            return .success
        }

        if self.clientErrorCode == NSURLErrorCancelled {
            return .canceled
        } else {
            return .failure
        }
    }
}
