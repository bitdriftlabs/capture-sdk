// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Metrics collected for the execution of an HTTP request.
public struct HTTPRequestMetrics {
    /// The number of body bytes sent over-the-wire.
    let requestBodyBytesSentCount: Int64?
    /// The number of response bytes received over-the-wire.
    let responseBodyBytesReceivedCount: Int64?

    /// The number of request HTTP headers bytes.
    let requestHeadersBytesCount: Int64?
    /// The number of response HTTP headers bytes.
    let responseHeadersBytesCount: Int64?

    /// The cumulative duration of all DNS resolution query(ies) performed during the execution
    /// of a given HTTP request.
    internal var dnsResolutionDuration: TimeInterval?

    /// Initializes a new instance of the receiver.
    ///
    /// - parameter requestBodyBytesSentCount:      The number of request body bytes sent over-the-wire.
    /// - parameter responseBodyBytesReceivedCount: The number of response body bytes received over-the-wire.
    /// - parameter requestHeadersBytesCount:       The number of request HTTP headers bytes.
    /// - parameter responseHeadersBytesCount:      The number of response HTTP headers bytes.
    /// - parameter dnsResolutionDuration:          The cumulative duration of all DNS resolution query(ies)
    ///                                             performed during the execution of a given HTTP request.
    public init(
        requestBodyBytesSentCount: Int64? = nil,
        responseBodyBytesReceivedCount: Int64? = nil,
        requestHeadersBytesCount: Int64? = nil,
        responseHeadersBytesCount: Int64? = nil,
        dnsResolutionDuration: TimeInterval? = nil
    ) {
        self.requestBodyBytesSentCount = requestBodyBytesSentCount
        self.responseBodyBytesReceivedCount = responseBodyBytesReceivedCount
        self.requestHeadersBytesCount = requestHeadersBytesCount
        self.responseHeadersBytesCount = responseHeadersBytesCount
        self.dnsResolutionDuration = dnsResolutionDuration
    }
}

extension HTTPRequestMetrics {
    init(metrics: URLSessionTaskMetrics) {
        if #available(iOS 13.0, *) {
            self.init(
                requestBodyBytesSentCount: metrics.transactionMetrics
                    .map(\.countOfRequestBodyBytesSent)
                    .reduce(0, +),
                responseBodyBytesReceivedCount: metrics.transactionMetrics
                    .map(\.countOfResponseBodyBytesReceived)
                    .reduce(0, +),
                requestHeadersBytesCount: metrics.transactionMetrics
                    .map(\.countOfRequestHeaderBytesSent)
                    .reduce(0, +),
                responseHeadersBytesCount: metrics.transactionMetrics
                    .map(\.countOfResponseHeaderBytesReceived)
                    .reduce(0, +),
                dnsResolutionDuration: Self.getDNSResolutionDuration(metrics: metrics)
            )
        } else {
            self.init(
                dnsResolutionDuration: Self.getDNSResolutionDuration(metrics: metrics)
            )
        }
    }

    private static func getDNSResolutionDuration(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        let dnsResolutionDurations = metrics.transactionMetrics
            .compactMap { metrics -> TimeInterval? in
                guard let startDate = metrics.domainLookupStartDate,
                      let endDate = metrics.domainLookupEndDate else
                {
                    return nil
                }

                return endDate.timeIntervalSince(startDate)
            }

        if !dnsResolutionDurations.isEmpty {
            return dnsResolutionDurations.reduce(0, +)
        } else {
            return nil
        }
    }
}
