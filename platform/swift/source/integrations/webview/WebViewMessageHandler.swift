// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
@preconcurrency import WebKit

/// Handles incoming messages from the WebView JavaScript bridge
final class WebViewMessageHandler: NSObject, WKScriptMessageHandler {
    private weak var logger: Logging?
    
    /// Whether the bridge has signaled it's ready
    var bridgeReady = false
    
    init(logger: Logging?) {
        self.logger = logger
        super.init()
    }
    
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard let body = message.body as? [String: Any] else {
            logger?.log(
                level: .warning,
                message: "Invalid message format from WebView bridge",
                fields: nil
            )
            return
        }
        
        handleMessage(body)
    }
    
    private func handleMessage(_ message: [String: Any]) {
        // Check protocol version
        guard let version = message["v"] as? Int, version == 1 else {
            let v = message["v"] as? Int ?? 0
            logger?.log(
                level: .warning,
                message: "Unsupported WebView bridge protocol version",
                fields: ["_version": String(v)]
            )
            return
        }
        
        guard let type = message["type"] as? String else { return }
        
        switch type {
        case "bridgeReady":
            handleBridgeReady(message)
        case "webVital":
            handleWebVital(message)
        case "networkRequest":
            handleNetworkRequest(message)
        case "navigation":
            handleNavigation(message)
        case "error":
            handleError(message)
        default:
            break
        }
    }
    
    private func handleBridgeReady(_ message: [String: Any]) {
        bridgeReady = true
        let url = message["url"] as? String ?? ""
        logger?.log(
            level: .debug,
            message: "WebView bridge ready",
            fields: ["_url": url]
        )
    }
    
    private func handleWebVital(_ message: [String: Any]) {
        guard let name = message["name"] as? String,
              let value = message["value"] as? Double else { return }
        
        let rating = message["rating"] as? String ?? "unknown"
        let navigationType = message["navigationType"] as? String
        
        var fields: Fields = [
            "_metric": name,
            "_value": String(value),
            "_rating": rating,
            "_source": "webview"
        ]
        
        if let navType = navigationType {
            fields["_navigationType"] = navType
        }
        
        let level: LogLevel
        switch rating {
        case "good":
            level = .debug
        case "needs-improvement":
            level = .info
        case "poor":
            level = .warning
        default:
            level = .debug
        }
        
        logger?.log(
            level: level,
            message: "webview.webVital.\(name)",
            fields: fields
        )
    }
    
    private func handleNetworkRequest(_ message: [String: Any]) {
        guard let urlString = message["url"] as? String else { return }
        
        let method = message["method"] as? String ?? "GET"
        let statusCode = message["statusCode"] as? Int ?? 0
        let durationMs = message["durationMs"] as? Int ?? 0
        let success = message["success"] as? Bool ?? false
        let error = message["error"] as? String
        let requestType = message["requestType"] as? String ?? "unknown"
        
        // Parse URL components
        let urlComponents = URLComponents(string: urlString)
        let host = urlComponents?.host
        let path = urlComponents?.path
        let query = urlComponents?.query
        
        // Build extra fields for webview context
        var extraFields: Fields = [
            "_source": "webview",
            "_request_type": requestType
        ]
        
        if let err = error {
            extraFields["_error"] = err
        }
        
        // Build metrics from timing data
        var metrics: HTTPRequestMetrics?
        if let timing = message["timing"] as? [String: Any] {
            let dnsMs = timing["dnsMs"] as? Double
            let connectMs = timing["connectMs"] as? Double
            let tlsMs = timing["tlsMs"] as? Double
            let ttfbMs = timing["ttfbMs"] as? Double
            let transferSize = timing["transferSize"] as? Int
            
            metrics = HTTPRequestMetrics(
                responseBodyBytesReceivedCount: transferSize.map { Int64($0) },
                dnsResolutionDuration: dnsMs.map { $0 / 1000.0 },
                tlsDuration: tlsMs.map { $0 / 1000.0 },
                tcpDuration: connectMs.map { $0 / 1000.0 },
                responseLatency: ttfbMs.map { $0 / 1000.0 }
            )
        }
        
        // Create request info
        let requestInfo = HTTPRequestInfo(
            method: method,
            host: host,
            path: path.map { HTTPURLPath(value: $0) },
            query: query,
            extraFields: extraFields
        )
        
        // Determine result
        let result: HTTPResponse.HTTPResult
        if !success {
            result = .failure
        } else {
            result = .success
        }
        
        // Create response
        let response = HTTPResponse(
            result: result,
            statusCode: statusCode > 0 ? statusCode : nil,
            error: nil
        )
        
        // Create response info
        let responseInfo = HTTPResponseInfo(
            requestInfo: requestInfo,
            response: response,
            duration: TimeInterval(durationMs) / 1000.0,
            metrics: metrics,
            extraFields: extraFields
        )
        
        // Log using native HTTP logging
        logger?.log(requestInfo, file: nil, line: nil, function: nil)
        logger?.log(responseInfo, file: nil, line: nil, function: nil)
    }
    
    private func handleNavigation(_ message: [String: Any]) {
        let fromUrl = message["fromUrl"] as? String ?? ""
        let toUrl = message["toUrl"] as? String ?? ""
        let method = message["method"] as? String ?? ""
        
        let fields: Fields = [
            "_fromUrl": fromUrl,
            "_toUrl": toUrl,
            "_method": method,
            "_source": "webview"
        ]
        
        logger?.log(
            level: .debug,
            message: "webview.navigation",
            fields: fields
        )
    }
    
    private func handleError(_ message: [String: Any]) {
        let errorMessage = message["message"] as? String ?? "Unknown error"
        let stack = message["stack"] as? String
        let filename = message["filename"] as? String
        let lineno = message["lineno"] as? Int
        let colno = message["colno"] as? Int
        
        var fields: Fields = [
            "_message": errorMessage,
            "_source": "webview"
        ]
        
        if let s = stack {
            fields["_stack"] = String(s.prefix(1000))
        }
        if let f = filename {
            fields["_filename"] = f
        }
        if let l = lineno {
            fields["_lineno"] = String(l)
        }
        if let c = colno {
            fields["_colno"] = String(c)
        }
        
        logger?.log(
            level: .error,
            message: "webview.error",
            fields: fields
        )
    }
}
