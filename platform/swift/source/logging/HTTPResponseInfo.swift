// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation

/// An object representing a HTTP response. Must be created from a corresponding `HTTPRequestLog` object.
public struct HTTPResponseInfo {
    // Keys used more than once.
    private enum Keys {
        static let result = "_result"
        static let statusCode = "_status_code"
    }

    private let requestInfo: HTTPRequestInfo

    let duration: TimeInterval
    let response: HTTPResponse
    let metrics: HTTPRequestMetrics?
    let extraFields: Fields?

    /// Initializes a response log using a provided request log.
    ///
    /// While response and request logs are logged separately, they are interconnected, and every
    /// response log contains all of the fields of the corresponding request log for matching purposes.
    /// To ensure key field uniqueness, the names of request log fields attached to the response log are
    /// prefixed with the "_request." string, so the field "query" from the request log becomes
    /// "_request.query" in the response log.
    ///
    /// `Host`, `path`, and `query` attributes captured by the request log are shared with the response log,
    /// with an option to override them by providing these attributes in the `HTTPResponse`. Refer to
    /// `HTTPResponse` for more details.
    ///
    /// - parameter requestInfo: The request log to associate the created response log with.
    /// - parameter response:    The HTTP response.
    /// - parameter duration:    The duration of a request, if any. It's recommended that you do not provide
    ///                          value in here and let the framework to calculate it for you.
    /// - parameter metrics:     The metrics collected for the execution of a given HTTP request.
    /// - parameter extraFields: Additional fields to be appended to the log. Optional.
    public init(
        requestInfo: HTTPRequestInfo,
        response: HTTPResponse,
        duration: TimeInterval? = nil,
        metrics: HTTPRequestMetrics? = nil,
        extraFields: Fields? = nil
    )
    {
        self.requestInfo = requestInfo
        self.response = response
        self.metrics = metrics
        self.extraFields = extraFields
        self.duration = duration ?? (Uptime().timeIntervalSince(requestInfo.startedAt))
    }

    // MARK: - Internal

    /// Gets a map of fields to use for logging a given response.
    ///
    /// - returns: A fields map.
    func toFields() -> Fields {
        // swiftlint:disable:previous cyclomatic_complexity
        var fields = self.requestInfo.toCommonFields()
        fields["_duration_ms"] = String(self.duration.toMilliseconds())

        // Extract url information (host, path, query) from response's URL if available. Otherwise, use
        // the value from requestInfo's common fields.
        if let host = self.response.host {
            fields[HTTPFieldKey.host.rawValue] = host
        }

        if let path = self.response.path {
            fields[HTTPFieldKey.path.rawValue] = path.value

            var requestPathTemplate: String?
            if self.requestInfo.path?.value == path.value {
                // If the path between request and response did not change and an explicit path template was
                // provided as part of a request use it as path template on a response.
                requestPathTemplate = self.requestInfo.path?.template
            }
            fields[HTTPFieldKey.pathTemplate.rawValue] = requestPathTemplate ?? path.template
        }

        if let query = self.response.query {
            fields[HTTPFieldKey.query.rawValue] = query
        }

        let result = self.response.result
        fields[Keys.statusCode] = self.response.statusCode.flatMap(String.init)

        switch result {
        case .success:
            fields[Keys.result] = "success"
        case .canceled:
            fields[Keys.result] = "canceled"
        case .failure:
            fields[Keys.result] = "failure"

            if let error = self.response.error {
                let foundationError = error as NSError?
                let foundationErrorCode = foundationError?.code

                fields["_error_message"] = foundationError?.localizedDescription
                // Render "_error_type" on the server-side using provided value of `_error_code`.
                // swiftlint:disable:next line_length
                // See https://developer.apple.com/documentation/foundation/1508628-url_loading_system_error_codes
                fields["_error_code"] = foundationErrorCode.flatMap(String.init)
            }
        }

        if let extraFields = self.extraFields {
            // TODO(Augustyniak): Allow custom extra fields from response to override custom
            // extra fields from request.
            fields.merge(extraFields) { old, _ in old }
        }

        if let metrics = self.metrics {
            if #available(iOS 13.0, *) {
                fields["_request_body_bytes_sent_count"] = metrics.requestBodyBytesSentCount
                    .flatMap(String.init)
                fields["_response_body_bytes_received_count"] = metrics.responseBodyBytesReceivedCount
                    .flatMap(String.init)
                fields["_request_headers_bytes_count"] = metrics.requestHeadersBytesCount
                    .flatMap(String.init)
                fields["_response_headers_bytes_count"] = metrics.responseHeadersBytesCount
                    .flatMap(String.init)
                fields["_dns_resolution_duration_ms"] = metrics.dnsResolutionDuration
                    .flatMap { "\($0.toMilliseconds())" }
            }
        }

        return fields
    }

    /// Gets a map of matching fields to use for logging a given response.
    ///
    /// - returns: A map of matching fields. These fields can be read when processing a given log but are
    ///            not a part of the log itself.
    func toMatchingFields() -> Fields {
        // We return all `HTTPRequestInfo` fields here, even if some are 1:1 duplicates of
        // `HTTPResponseField`'s fields (e.g., `_host`, `_method`, and `_path`). This is to facilitate
        // clarity in understanding what is sent as part of matching fields. In the future, we may want to
        // reduce the duplication of fields sent as part of the normal (non-matching) fields of
        // `HTTPRequestInfo` and `HTTPResponseInfo`.
        Dictionary(
            uniqueKeysWithValues:
                self.requestInfo.toFields().map { key, value in
                    ("_request.\(key)", value)
                }
                + self.requestInfo.toMatchingFields().map { key, value in
                    ("_request.\(key)", value)
                }
                + (self.response.headers.flatMap { HTTPHeaders.normalizeHeaders($0) } ?? [])
        )
    }
}

private extension TimeInterval {
    func toMilliseconds() -> Int64 {
        return Int64(self * 1_000)
    }
}
