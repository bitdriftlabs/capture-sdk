// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct NetworkRequestMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let requestId: String
    let method: String
    let url: String
    let statusCode: Int
    let durationMs: Double
    let success: Bool
    let error: String?
    let requestType: String
    let timing: WebViewResourceTiming?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        guard let components = URLComponents(string: url) else {
            return nil
        }

        let path = components.path.isEmpty ? nil : HTTPURLPath(value: components.path, template: nil)
        let request = HTTPRequestInfo(
            method: method,
            host: components.host,
            path: path,
            query: components.query,
            spanID: requestId,
            extraFields: makeFields(
                ("_request_type", requestType)
            )
        )

        let response = HTTPResponse(
            result: success ? .success : .failure,
            statusCode: statusCode,
            error: error.map(WebViewNetworkError.init(message:))
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: request,
            response: response,
            duration: durationMs / 1_000,
            metrics: timing?.httpRequestMetrics
        )

        return .network(request: request, response: responseInfo)
    }
}

struct WebViewNetworkError: LocalizedError {
    let message: String

    var errorDescription: String? {
        message
    }
}

struct WebViewResourceTiming: Codable, Equatable {
    let name: String?
    let entryType: String?
    let startTime: Double?
    let duration: Double?
    let initiatorType: String?
    let nextHopProtocol: String?
    let workerStart: Double?
    let redirectStart: Double?
    let redirectEnd: Double?
    let fetchStart: Double?
    let domainLookupStart: Double?
    let domainLookupEnd: Double?
    let connectStart: Double?
    let connectEnd: Double?
    let secureConnectionStart: Double?
    let requestStart: Double?
    let responseStart: Double?
    let responseEnd: Double?
    let transferSize: Int?
    let encodedBodySize: Int?
    let decodedBodySize: Int?
    let responseStatus: Int?
    let serverTiming: [WebViewServerTiming]?

    var httpRequestMetrics: HTTPRequestMetrics {
        HTTPRequestMetrics(
            responseBodyBytesReceivedCount: transferSize.map(Int64.init),
            dnsResolutionDuration: duration(from: domainLookupStart, to: domainLookupEnd),
            tlsDuration: tlsDuration,
            tcpDuration: tcpDuration,
            responseLatency: duration(from: requestStart, to: responseStart),
            protocolName: nextHopProtocol
        )
    }

    var tcpDuration: TimeInterval? {
        if let secureConnectionStart, secureConnectionStart > 0 {
            return duration(from: connectStart, to: secureConnectionStart)
        }

        return duration(from: connectStart, to: connectEnd)
    }

    var tlsDuration: TimeInterval? {
        guard let secureConnectionStart, secureConnectionStart > 0 else {
            return nil
        }

        return duration(from: secureConnectionStart, to: connectEnd)
    }

    func duration(from start: Double?, to end: Double?) -> TimeInterval? {
        guard let start, let end, end >= start else {
            return nil
        }

        return (end - start) / 1_000
    }
}
