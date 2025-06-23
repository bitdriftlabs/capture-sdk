// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

let kCapturePathTemplateHeaderKey = "x-capture-path-template"

enum HTTPFieldKey: String {
    case host = "_host"
    case path = "_path"
    case pathTemplate = "_path_template"
    case query = "_query"
    case graphQLOperationName = "_operation_name"
    case graphQLOperationType = "_operation_type"
    case graphQLOperationID = "_operation_id"
}

/// An object representing an HTTP request.
public struct HTTPRequestInfo {
    let method: String
    let host: String?
    let path: HTTPURLPath?
    let query: String?
    let headers: [String: String]?
    let bytesExpectedToSendCount: Int64?

    let spanID: String
    let extraFields: Fields?
    let startedAt = Uptime()

    /// Initializes a request log.
    ///
    /// - parameter method:                   The HTTP method used (e.g. "GET").
    /// - parameter host:                     The host (e.g. "example.com").
    /// - parameter path:                     The path (e.g. "/v1/test/ping"). If it doesn't specify a path
    ///                                       template, the SDK detects and replaces high-cardinality path
    ///                                       portions with the "<id>" placeholder.
    /// - parameter query:                    The query (e.g. "utm_source=test").
    /// - parameter headers:                  The request headers.
    /// - parameter bytesExpectedToSendCount: The count of the bytes in the request's body.
    /// - parameter spanID:                   A span ID that identifies the HTTP request / response. If none
    ///                                       is provided, a UUID string will be generated.
    /// - parameter extraFields:              Additional fields to be appended to the network log. Optional.
    ///
    public init(
        method: String,
        host: String? = nil,
        path: HTTPURLPath? = nil,
        query: String? = nil,
        headers: [String: String]? = nil,
        bytesExpectedToSendCount: Int64? = nil,
        spanID: String = UUID().uuidString,
        extraFields: Fields? = nil
    )
    {
        self.method = method
        self.host = host
        self.path = path
        self.query = query
        self.headers = headers
        self.spanID = spanID
        self.bytesExpectedToSendCount = bytesExpectedToSendCount
        self.extraFields = extraFields
    }

    // MARK: - Internal

    /// Gets a map of fields to use for logging a given request.
    ///
    /// - returns: A fields map.
    func toFields() -> Fields {
        var fields = self.toCommonFields()

        if let bytesExpectedToSendCount = self.bytesExpectedToSendCount, bytesExpectedToSendCount > 0 {
            // Do not put body bytes count as a common field since response log has a more accurate
            // measurement of request' body count anyway.
            fields["_request_body_bytes_expected_to_send_count"] =
                String(describing: bytesExpectedToSendCount)
        }

        // Best effort to extract graphQL operation name from the headers, this is specific
        // to Apollo iOS client.
        if let headers = self.headers {
            if let operationName = headers["X-APOLLO-OPERATION-NAME"] {
                fields[HTTPFieldKey.graphQLOperationName.rawValue] = operationName
                fields[HTTPFieldKey.pathTemplate.rawValue] = "gql-\(operationName)"
            }

            if let operationType = headers["X-APOLLO-OPERATION-TYPE"] {
                fields[HTTPFieldKey.graphQLOperationType.rawValue] = operationType
            }

            if let operationID = headers["X-APOLLO-OPERATION-ID"] {
                fields[HTTPFieldKey.graphQLOperationID.rawValue] = operationID
            }
        }

        return fields
    }

    /// Returns fields that are supposed to be shared between request and response logs.
    ///
    /// - returns: Fields to share with response logs.
    func toCommonFields() -> Fields {
        var fields: [String: Encodable] = [
            "_method": self.method,
            "_span_id": self.spanID,
        ]

        if let host = self.host {
            fields[HTTPFieldKey.host.rawValue] = host
        }

        if let path = self.path {
            fields[HTTPFieldKey.path.rawValue] = path.value
            fields[HTTPFieldKey.pathTemplate.rawValue] = path.template
        }

        if let query = self.query {
            fields[HTTPFieldKey.query.rawValue] = query
        }

        // The OOTB fields take precedence
        if let extraFields = self.extraFields {
            fields.merge(extraFields) { old, _ in old }
        }

        return fields
    }

    /// Gets a map of matching fields to use for logging a given request.
    ///
    /// - returns: A map of matching fields. These fields can be read when processing a given log but are
    ///            not a part of the log itself.
    func toMatchingFields() -> Fields {
        guard let headers = self.headers else {
            return [:]
        }

        return HTTPHeaders.normalizeHeaders(headers)
    }
}

extension HTTPRequestInfo {
    /// Initializes a new instance of the receiver using a provided `URLRequest`.
    ///
    /// - parameter urlRequest:               The request whose field should be used to initialize a new
    ///                                       instance of request log.
    /// - parameter bytesExpectedToSendCount: The number of bytes the task expects to send in request body.
    ///                                       If not provided, the implementation uses to the number of
    ///                                       bytes of request's `httpBody`.
    public init(urlRequest: URLRequest, bytesExpectedToSendCount: Int64? = nil) {
        let bytesExpectedToSendCount =
            bytesExpectedToSendCount ?? urlRequest.httpBody.flatMap { Int64($0.count) }
        self.init(
            method: urlRequest.httpMethod ?? "",
            host: urlRequest.url?.host,
            path: urlRequest.url?.normalizedPath().flatMap {
                HTTPURLPath(
                    value: $0,
                    template: urlRequest.value(forHTTPHeaderField: kCapturePathTemplateHeaderKey)
                )
            },
            query: urlRequest.url?.query,
            headers: urlRequest.allHTTPHeaderFields,
            bytesExpectedToSendCount: bytesExpectedToSendCount
        )
    }

    /// Initializes a new instance of the receiver using a provided `URLSessionTask`. The initialization
    /// succeeds as long as `originalRequest` property of the passed task is not equal to `nil`.
    ///
    /// - parameter task: The task to initialize the request info with.
    public init?(task: URLSessionTask) {
        if let request = task.originalRequest {
            if task is URLSessionDataTask {
                // For basic data tasks we just use `countOfBytesExpectedToSend` for request body estimate.
                self.init(urlRequest: request, bytesExpectedToSendCount: task.countOfBytesExpectedToSend)
            } else {
                // Task types such as upload tasks are a bit more tricky since they do not have `httpBody`
                // property set and their value of `countOfBytesExpectedToSend` may be equal to 0 even if
                // they do end up uploading body bytes. For this reason, use `countOfBytesExpectedToSend`
                // only if it's greater than 0.
                let bytesExpectedToSendCount =
                    task.countOfBytesExpectedToSend > 0 ? task.countOfBytesExpectedToSend : nil
                self.init(urlRequest: request, bytesExpectedToSendCount: bytesExpectedToSendCount)
            }
        } else {
            return nil
        }
    }
}
