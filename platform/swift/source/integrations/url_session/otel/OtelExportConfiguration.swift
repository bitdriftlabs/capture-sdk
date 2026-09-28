// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Configuration for exporting an OpenTelemetry `CLIENT` span for each traced network request,
/// reusing the exact trace/span ID already injected into that request's header (see
/// `URLSessionTraceContext`). Pass `nil` (the default) to disable the feature.
///
/// This API is experimental and may change in the future.
public struct OtelExportConfiguration {
    /// The OTLP/HTTP endpoint spans are POSTed to (e.g. a ClickStack/HyperDX `/v1/traces` URL).
    public let endpoint: URL

    /// The value of the auth header sent with each export request.
    public let authHeaderValue: String

    /// The name of the auth header sent with each export request. Defaults to `"authorization"`.
    public let authHeaderName: String

    public init(endpoint: URL, authHeaderValue: String, authHeaderName: String = "authorization") {
        self.endpoint = endpoint
        self.authHeaderValue = authHeaderValue
        self.authHeaderName = authHeaderName
    }
}
