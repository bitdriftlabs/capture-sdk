// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Metrics collected for the execution of an HTTP request.
///
/// See https://developer.apple.com/documentation/foundation/urlsessiontasktransactionmetrics for
/// the flow of these events.
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
    let dnsResolutionDuration: TimeInterval?

    /// The cumulative duration of all TLS handshakes performed during the execution of a given HTTP request.
    let tlsDuration: TimeInterval?

    /// The cumulative duration of all connection establishments performed during the execution of a given HTTP request.
    let tcpDuration: TimeInterval?

    /// The cumulative duration of all connection establishments performed during the execution of a given HTTP request.
    let fetchInitializationDuration: TimeInterval?

    /// The cumulative duration of all responses from the time the request is sent to the time we get the first byte from the server
    let responseLatency: TimeInterval?

    /// The protocol used on the request, e.g. (http/1.0, http/1.1, etc).
    let protocolName: String?

    /// Initializes a new instance of the receiver.
    ///
    /// - parameter requestBodyBytesSentCount:      The number of request body bytes sent over-the-wire.
    /// - parameter responseBodyBytesReceivedCount: The number of response body bytes received over-the-wire.
    /// - parameter requestHeadersBytesCount:       The number of request HTTP headers bytes.
    /// - parameter responseHeadersBytesCount:      The number of response HTTP headers bytes.
    /// - parameter dnsResolutionDuration:          The cumulative duration of all DNS resolution query(ies)
    ///                                             performed
    ///                                             during the execution of a given HTTP request.
    /// - parameter tlsDuration:                    The cumulative duration of all TLS handshakes performed
    ///                                             during
    ///                                             the execution of a given HTTP request.
    /// - parameter tcpDuration:                    The cumulative duration of all connection establishments
    ///                                             performed during the execution of a given HTTP request.
    /// - parameter fetchInitializationDuration:    The cumulative duration of all connection establishments
    ///                                             performed during the execution of a given HTTP request.
    /// - parameter responseLatency:                The cumulative duration of all responses from the time the
    ///                                             request is sent to the time we get the first byte from the
    ///                                             server.
    /// - parameter protocolName:                   The protocol used on the request, e.g. (http/1.0,
    ///                                             http/1.1, etc).
    public init(
        requestBodyBytesSentCount: Int64? = nil,
        responseBodyBytesReceivedCount: Int64? = nil,
        requestHeadersBytesCount: Int64? = nil,
        responseHeadersBytesCount: Int64? = nil,
        dnsResolutionDuration: TimeInterval? = nil,
        tlsDuration: TimeInterval? = nil,
        tcpDuration: TimeInterval? = nil,
        fetchInitializationDuration: TimeInterval? = nil,
        responseLatency: TimeInterval? = nil,
        protocolName: String? = nil
    ) {
        self.requestBodyBytesSentCount = requestBodyBytesSentCount
        self.responseBodyBytesReceivedCount = responseBodyBytesReceivedCount
        self.requestHeadersBytesCount = requestHeadersBytesCount
        self.responseHeadersBytesCount = responseHeadersBytesCount
        self.dnsResolutionDuration = dnsResolutionDuration
        self.tlsDuration = tlsDuration
        self.tcpDuration = tcpDuration
        self.fetchInitializationDuration = fetchInitializationDuration
        self.responseLatency = responseLatency
        self.protocolName = protocolName
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
                dnsResolutionDuration: Self.getDNSResolutionDuration(metrics: metrics),
                tlsDuration: Self.getTLSDuration(metrics: metrics),
                tcpDuration: Self.getTCPDuration(metrics: metrics),
                fetchInitializationDuration: Self.getInitializationDuration(metrics: metrics),
                responseLatency: Self.getResponseLatency(metrics: metrics),
                protocolName: metrics.transactionMetrics.last?.networkProtocolName
            )
        } else {
            self.init(
                dnsResolutionDuration: Self.getDNSResolutionDuration(metrics: metrics),
                tlsDuration: Self.getTLSDuration(metrics: metrics),
                tcpDuration: Self.getTCPDuration(metrics: metrics),
                fetchInitializationDuration: Self.getInitializationDuration(metrics: metrics),
                responseLatency: Self.getResponseLatency(metrics: metrics)
            )
        }
    }

    private static func reduce(metrics: URLSessionTaskMetrics,
                               startPath: KeyPath<URLSessionTaskTransactionMetrics, Date?>,
                               endPath: KeyPath<URLSessionTaskTransactionMetrics, Date?>) -> TimeInterval?
    {
        let durations = metrics.transactionMetrics
            .compactMap { metrics -> TimeInterval? in
                guard let startDate = metrics[keyPath: startPath],
                      let endDate = metrics[keyPath: endPath] else
                {
                    return nil
                }

                return endDate.timeIntervalSince(startDate)
            }

        if !durations.isEmpty {
            return durations.reduce(0, +)
        } else {
            return nil
        }
    }

    private static func getResponseLatency(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        Self.reduce(metrics: metrics, startPath: \.requestEndDate, endPath: \.responseStartDate)
    }

    private static func getInitializationDuration(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        Self.reduce(metrics: metrics, startPath: \.fetchStartDate, endPath: \.domainLookupStartDate)
    }

    private static func getTCPDuration(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        guard let secureTCP = Self.reduce(metrics: metrics, startPath: \.connectStartDate,
                                          endPath: \.secureConnectionStartDate) else
        {
            return Self.reduce(metrics: metrics, startPath: \.connectStartDate,
                               endPath: \.connectEndDate)
        }

        return secureTCP
    }

    private static func getTLSDuration(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        Self.reduce(metrics: metrics, startPath: \.secureConnectionStartDate,
                    endPath: \.secureConnectionEndDate)
    }

    private static func getDNSResolutionDuration(metrics: URLSessionTaskMetrics) -> TimeInterval? {
        Self.reduce(metrics: metrics, startPath: \.domainLookupStartDate,
                    endPath: \.domainLookupEndDate)
    }
}
